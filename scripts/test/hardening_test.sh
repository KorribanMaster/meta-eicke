#!/bin/bash
# Unattended verification of the security-hardening changes on qemux86-64
# (eicke-image-prod). Runs INSIDE the build container after sourcing
# integration-init-build-env (see test-env.sh). The image must be built with
# EICKE_SSH_PUBKEY = the test key's .pub, and -- like every prod image -- with
# verified boot (EICKE_VERIFIED_BOOT=1 plus the x86 Secure Boot knobs from
# local.conf.sample; see doc/security.md).
set -u
. "$(dirname "$0")/test-env.sh"

DEPLOY=$DEPLOY_DIR/qemux86-64
SWU=$DEPLOY/eicke-update-image-prod-qemux86-64.rootfs.swu
SSH="ssh $SSH_OPTS"

fail() { echo "HARDENTEST-FAIL: $1"; kill %1 2>/dev/null; exit 1; }
pass() { echo "HARDENTEST-PASS: $1"; }

runqemu qemux86-64 eicke-image-prod wic ovmf nographic slirp snapshot \
    "qemuparams=-drive file=$SWU,if=virtio,format=raw" > "$BUILDDIR/qemu-serial-hardening.log" 2>&1 &

up=0
for i in $(seq 120); do
    $SSH root@127.0.0.1 true 2>/dev/null && { up=1; break; }
    sleep 5
done
[ "$up" = 1 ] || fail "guest never reachable over SSH (key auth)"
pass "boot + SSH key auth"

# --- WP1a: sshd policy ---
out=$($SSH root@127.0.0.1 "sshd -T 2>/dev/null | grep -E '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '")
echo "$out" | grep -q '^passwordauthentication no' || fail "PasswordAuthentication not 'no': $out"
echo "$out" | grep -q '^kbdinteractiveauthentication no' || fail "KbdInteractiveAuthentication not 'no': $out"
echo "$out" | grep -q '^permitrootlogin prohibit-password' || fail "PermitRootLogin not prohibit-password: $out"
pass "sshd -T policy (no password / no kbdint / prohibit-password)"

ssh -p 2222 -o PreferredAuthentications=password -o PubkeyAuthentication=no \
    -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=5 \
    -o NumberOfPasswordPrompts=0 -o LogLevel=ERROR root@127.0.0.1 true 2>/dev/null \
    && fail "password auth was ACCEPTED"
pass "password SSH auth refused"

# --- WP2a: kernel ---
$SSH root@127.0.0.1 "cat /sys/kernel/security/lockdown" | grep -q '\[integrity\]' || fail "lockdown not integrity"
pass "kernel lockdown [integrity]"
$SSH root@127.0.0.1 "test ! -e /dev/mem && test ! -e /proc/kcore" || fail "/dev/mem or /proc/kcore present"
pass "/dev/mem and /proc/kcore absent"
$SSH root@127.0.0.1 "modinfo r8169 2>/dev/null | grep -qi '^sig_id\|^signer'" || fail "r8169 not signed (modinfo)"
pass "kernel module signature present (r8169)"

# --- WP2b: sysctls ---
vals=$($SSH root@127.0.0.1 "sysctl -n kernel.yama.ptrace_scope kernel.dmesg_restrict kernel.kptr_restrict kernel.unprivileged_bpf_disabled net.ipv4.tcp_syncookies fs.protected_regular" | tr '\n' ' ')
[ "$vals" = "1 1 2 1 1 2 " ] || fail "sysctls wrong: $vals"
pass "hardening sysctls applied ($vals)"

# --- WP3: firewall ---
$SSH root@127.0.0.1 "systemctl is-active eicke-firewall" | grep -qx active || fail "eicke-firewall not active"
$SSH root@127.0.0.1 "nft list ruleset" | grep -q 'policy drop' || fail "nft input policy not drop"
pass "eicke-firewall active, default-drop ruleset loaded"
# 8080 (mongoose): listening in the guest and explicitly accepted by the
# ruleset (slirp only forwards 2222->22, so no outside probe from here).
# no netstat/ss on the image; 8080 = 0x1F90 in /proc/net/tcp (state 0A = LISTEN)
$SSH root@127.0.0.1 "grep -qE ':1F90 .* 0A ' /proc/net/tcp /proc/net/tcp6" || fail "mongoose not listening on 8080"
$SSH root@127.0.0.1 "nft list chain inet filter input" | grep -q 'dport 8080 accept' || fail "no nft accept rule for 8080"
pass "swupdate web UI listening + firewall accept rule present"

# --- WP1b: hw-compat ---
$SSH root@127.0.0.1 "cat /etc/hwrevision" | grep -qx 'eicke qemux86-64' || fail "/etc/hwrevision wrong"
pass "/etc/hwrevision = 'eicke qemux86-64'"
# The .swu is the attached virtio drive; on qemux86-64 the wic disk is sda,
# so the bundle may be vda. Detect it by the cpio (newc) magic.
# magic 070702: swupdate builds the cpio in crc format (070701 = plain newc)
SWUDEV=$($SSH root@127.0.0.1 'for d in /dev/vda /dev/vdb; do [ -b $d ] && head -c 6 $d 2>/dev/null | grep -qE "07070[12]" && { echo $d; break; }; done')
[ -n "$SWUDEV" ] || fail "no .swu virtio drive found in guest"
# CLI output only says "Image invalid or corrupted" for a HW-compat reject
# (the detailed reason is in the daemon journal); the discriminator is that
# the SAME bundle installs fine without the bogus -H override below.
bogus=$($SSH root@127.0.0.1 "swupdate -H eicke:bogus-machine -i $SWUDEV" 2>&1)
echo "$bogus" | grep -q 'SWUPDATE successful' && fail "swupdate ACCEPTED bundle under bogus hwrevision"
echo "$bogus" | grep -q 'SWUPDATE failed' || fail "no explicit failure from swupdate: $bogus"
$SSH root@127.0.0.1 "journalctl -u swupdate --no-pager | tail -20" | grep -qi 'compatible\|compatib' \
    && pass "swupdate refuses bundle for wrong hardware revision (journal confirms HW check)" \
    || pass "swupdate refuses bundle for wrong hardware revision"
$SSH root@127.0.0.1 "swupdate -i $SWUDEV" 2>&1 | grep -q 'SWUPDATE successful' || fail "swupdate correct-machine install failed"
pass "swupdate installs correct-machine bundle (A/B write OK)"

# --- general health ---
state=$($SSH root@127.0.0.1 "systemctl is-system-running" 2>/dev/null)
{ [ "$state" = running ] || [ "$state" = degraded ]; } || fail "system state: $state"
[ "$state" = running ] || echo "HARDENTEST-WARN: system degraded:" "$($SSH root@127.0.0.1 'systemctl --failed --no-legend' 2>/dev/null)"
pass "boot health: $state"

kill %1 2>/dev/null
echo "HARDENTEST: ALL PASSED"
