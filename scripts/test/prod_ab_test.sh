#!/bin/bash
# Unattended A/B + rollback verification for eicke-image-prod on qemuarm-uboot,
# built WITH verified boot (EICKE_VERIFIED_BOOT=1: signed kernel FIT per slot,
# signed .swu). Runs INSIDE the build container after sourcing
# integration-init-build-env (see test-env.sh).
#
#   prod_ab_test.sh --build   build the prod image + bundle first, then test
#   prod_ab_test.sh           test the already-deployed prod image + bundle
#
# --build passes EICKE_VERIFIED_BOOT=1 and EICKE_SSH_PUBKEY (the test key's
# .pub, see test-env.sh) to bitbake through the environment, so
# local.conf/auto.conf stay untouched. It needs the key store (EICKE_KEYS_DIR,
# /yocto/keys: eicke-fit.{key,crt}, swupdate-priv.pem; scripts/eicke-gen-keys.sh).
# Without --build the deployed image must have been built the same way: the
# guest is driven over SSH with the test key (serial login needs the real root
# password).
set -u
. "$(dirname "$0")/test-env.sh"

DEPLOY=$DEPLOY_DIR/qemuarm-uboot
SWU=$DEPLOY/eicke-update-image-prod-qemuarm-uboot.rootfs.swu
LOG=$BUILDDIR/qemu-serial-prod.log
SSH="ssh $SSH_OPTS root@127.0.0.1"

fail() { echo "PRODTEST-FAIL: $1"; kill %1 2>/dev/null; exit 1; }
pass() { echo "PRODTEST-PASS: $1"; }

if [ "${1:-}" = "--build" ]; then
    EICKE_VERIFIED_BOOT=1 EICKE_SSH_PUBKEY="$(cat "$EICKE_TEST_KEY.pub")" \
    BB_ENV_PASSTHROUGH_ADDITIONS="${BB_ENV_PASSTHROUGH_ADDITIONS:-} EICKE_VERIFIED_BOOT EICKE_SSH_PUBKEY" \
    MACHINE=qemuarm-uboot bitbake eicke-image-prod eicke-update-image-prod \
        || fail "build (verified boot)"
    pass "verified-boot build"
fi

wait_ssh() { # $1 = expected slot, $2 = tries (x5s)
    for i in $(seq "$2"); do
        slot=$($SSH cat /proc/cmdline 2>/dev/null | grep -o 'eicke.slot=rootfs_[ab]')
        [ -n "$slot" ] && { echo "guest up: $slot"; echo "$slot" | grep -q "$1" && return 0 || return 1; }
        sleep 5
    done
    return 1
}

# U-Boot printed the signed-FIT path for slot $1 (the bootcmd falls back to the
# plain zImage when a slot has no /boot/fitImage, so check it every boot).
# $2 = how many FIT boots of that slot the serial log must show by now.
check_fit() {
    n=$(grep -c "Eicke Linux ($1): booting signed /boot/fitImage" "$LOG")
    [ "$n" -ge "$2" ] || fail "$1 did not boot the signed FIT (see $LOG)"
    grep -q "Verifying Hash Integrity.*rsa2048:eicke-fit" "$LOG" \
        || fail "FIT carries no eicke-fit rsa2048 signature"
    pass "$1 booted the signed kernel FIT"
}

runqemu qemuarm-uboot eicke-image-prod wic nographic slirp snapshot \
    "qemuparams=-drive file=$SWU,if=virtio,format=raw" > "$LOG" 2>&1 &

wait_ssh rootfs_a 180 || fail "slot A ssh/cmdline"
pass "boot slot A + SSH key auth"
check_fit rootfs_a 1

$SSH "awk '\$2==\"/\" {print \$4}' /proc/mounts" | grep -q '^ro,' || fail "rootfs not read-only"
pass "read-only rootfs"

$SSH "awk '\$2==\"/etc\" && \$3==\"overlay\"' /proc/mounts" | grep -q overlay || fail "/etc overlay missing"
# The overlayfs-etc preinit must have mounted /data from the boot disk (via
# eicke-bootpart), and the udev links must name the same partition.
data=$($SSH "findmnt -no SOURCE /data")
[ -n "$data" ] || fail "/data not mounted"
[ "$data" = "$($SSH /usr/libexec/eicke-bootpart data)" ] || fail "/data is $data, not the boot disk's data partition"
[ "$($SSH readlink -f /dev/eicke/data)" = "$data" ] || fail "/dev/eicke/data does not point at $data"
pass "/etc overlayfs on /data ($data, boot disk)"

# A bundle whose sw-description no longer matches its signature is refused
# (only the first MiB is needed: sw-description + .sig come first in the cpio).
$SSH "dd if=/dev/vdb of=/data/tampered.swu bs=1M count=1 2>/dev/null && printf X | dd of=/data/tampered.swu bs=1 seek=300 conv=notrunc 2>/dev/null"
out=$($SSH "swupdate -c -i /data/tampered.swu" 2>&1) && fail "tampered bundle was ACCEPTED"
$SSH "rm -f /data/tampered.swu"
echo "$out" | grep -qi 'signature' || fail "tampered bundle refused, but not for its signature: $out"
pass "tampered bundle refused"

$SSH "fw_printenv -n ustate" | grep -qx 0 || fail "initial ustate not 0"
$SSH "swupdate -i /dev/vdb" 2>&1 | grep -q 'SWUPDATE successful' || fail "swupdate"
$SSH "fw_printenv -n rootdev" | grep -qx rootfs_b || fail "rootdev not flipped"
$SSH "fw_printenv -n ustate" | grep -qx 1 || fail "trial not armed"
pass "signed prod update applied, trial armed"

$SSH reboot 2>/dev/null; sleep 30
wait_ssh rootfs_b 180 || fail "slot B boot"
check_fit rootfs_b 1
sleep 20   # let eicke-bootconfirm finish
$SSH "fw_printenv -n ustate" | grep -qx 0 || fail "bootconfirm did not clear ustate"
pass "slot B confirmed (ustate=0)"

$SSH "fw_setenv ustate 1 && fw_setenv bootcount 1" || fail "arming simulated failure"
$SSH reboot 2>/dev/null; sleep 30
wait_ssh rootfs_a 180 || fail "rollback to slot A"
check_fit rootfs_a 2
$SSH "fw_printenv -n ustate" | grep -qx 3 || fail "ustate not 3 after rollback"
pass "automatic rollback to slot A (ustate=3)"

$SSH "fw_setenv ustate 0"
$SSH poweroff 2>/dev/null; sleep 10
kill %1 2>/dev/null
echo "PRODTEST-ALL-PASS"
