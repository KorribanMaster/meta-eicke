#!/bin/bash
# Unattended A/B + rollback verification for eicke-image-prod on qemuarm-uboot,
# built WITH verified boot (EICKE_VERIFIED_BOOT=1: signed kernel FIT per slot,
# signed .swu). Runs INSIDE the build container after sourcing
# integration-init-build-env (see test-env.sh).
#
#   prod_ab_test.sh --build   build the prod image + bundle first, then test
#   prod_ab_test.sh           test the already-deployed prod image + bundle
#
# Prefer --build (mostly sstate): any bitbake run for qemuarm-uboot without
# EICKE_VERIFIED_BOOT=1 sees eicke-image-prod as skipped and deletes its
# deployed image as stale, so a previously built image may be gone.
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

# Exported for runqemu as well: it runs `bitbake -e eicke-image-prod` to find
# the image, and without the switch the prod recipe is skipped.
export EICKE_VERIFIED_BOOT=1
EICKE_SSH_PUBKEY="$(cat "$EICKE_TEST_KEY.pub")"
export EICKE_SSH_PUBKEY
export BB_ENV_PASSTHROUGH_ADDITIONS="${BB_ENV_PASSTHROUGH_ADDITIONS:-} EICKE_VERIFIED_BOOT EICKE_SSH_PUBKEY"

if [ "${1:-}" = "--build" ]; then
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

# U-Boot booted slot $1 from /boot/fitImage (the bootcmd falls back to the
# plain zImage when a slot has no FIT, so check it every boot), and the
# running slot's FIT carries the eicke-fit rsa2048 signature. U-Boot itself
# only checks the FIT hashes under QEMU: the key would live in its control
# DTB, which QEMU supplies (doc/security.md, enforcement deferred).
# $2 = how many FIT boots of that slot the serial log must show by now.
check_fit() {
    n=$(grep -c "Eicke Linux ($1): booting signed /boot/fitImage" "$LOG")
    [ "$n" -ge "$2" ] || fail "$1 did not boot the FIT (see $LOG)"
    # The slot's /boot is hidden under the boot partition mounted on /boot;
    # a bind mount of / shows the rootfs's own /boot/fitImage.
    $SSH "mkdir -p /tmp/slotroot && mount --bind / /tmp/slotroot &&
          grep -q 'eicke-fit' /tmp/slotroot/boot/fitImage &&
          grep -q 'sha256,rsa2048' /tmp/slotroot/boot/fitImage; rc=\$?;
          umount /tmp/slotroot; exit \$rc" \
        || fail "$1: the slot's /boot/fitImage carries no eicke-fit rsa2048 signature"
    pass "$1 booted its signed kernel FIT"
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
data=$($SSH "awk '\$2==\"/data\" {print \$1}' /proc/mounts")
[ -n "$data" ] || fail "/data not mounted"
[ "$data" = "$($SSH /usr/libexec/eicke-bootpart data)" ] || fail "/data is $data, not the boot disk's data partition"
[ "$($SSH readlink -f /dev/eicke/data)" = "$data" ] || fail "/dev/eicke/data does not point at $data"
pass "/etc overlayfs on /data ($data, boot disk)"

# A bundle whose sw-description no longer matches its signature is refused
# (only the first MiB is needed: sw-description + .sig come first in the
# cpio). Two different bytes inside sw-description are swapped, which keeps
# the cpio (crc format) checksum valid, so the RSA check is what has to fail.
# python3 because busybox dd has no conv=notrunc.
$SSH "dd if=/dev/vdb of=/data/tampered.swu bs=1M count=1 2>/dev/null && python3 -c '
f = open(\"/data/tampered.swu\", \"r+b\")
f.seek(300); b = f.read(2); assert b[0] != b[1]
f.seek(300); f.write(b[::-1]); f.close()'" || fail "could not prepare the tampered bundle"
out=$($SSH "swupdate -v -c -i /data/tampered.swu" 2>&1) && fail "tampered bundle was ACCEPTED"
$SSH "rm -f /data/tampered.swu"
echo "$out" | grep -q 'Error Verifying Data' || fail "tampered bundle refused, but not by the signature check: $out"
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
