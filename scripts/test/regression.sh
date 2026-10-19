#!/bin/bash
# QEMU regression run over the A/B machines. Runs INSIDE the build container
# (with /dev/kvm) after sourcing integration-init-build-env (see test-env.sh).
#
#   regression.sh [--build] [test...]
#
# --build first builds every image the tests boot. Tests (default: all):
#   arm-ab        qemuarm-uboot eicke-image: A/B update, confirm, rollback
#   arm-lockdown  qemuarm-uboot: a tampered U-Boot env cannot inject cmdline
#   x86-ab        qemux86-64 eicke-image (GRUB/OVMF): A/B, confirm, rollback
#   imx8mp        qemuimx8mp-evk eicke-image-dev: M7 echo ptest, A/B update,
#                 confirm on slot B, ptest again
#   prod-ab       qemuarm-uboot eicke-image-prod with verified boot
#                 (prod_ab_test.sh; built by it with --build)
# Serial logs: $BUILDDIR/qemu-serial-<script>.log. Prints one
# "REG <test> rc=<n>" line per test and exits non-zero if any failed.
set -u
. "$(dirname "$0")/test-env.sh"

build=0
[ "${1:-}" = "--build" ] && { build=1; shift; }
tests=${*:-arm-ab arm-lockdown x86-ab imx8mp prod-ab}

if [ $build = 1 ]; then
    MACHINE=qemuarm-uboot bitbake eicke-image eicke-update-image || exit 1
    MACHINE=qemux86-64 bitbake eicke-image eicke-update-image ovmf || exit 1
    MACHINE=qemuimx8mp-evk bitbake eicke-image-dev eicke-update-image-dev \
        qemu-helper-native || exit 1
fi

arm_swu=$DEPLOY_DIR/qemuarm-uboot/eicke-update-image-qemuarm-uboot.rootfs.swu
x86_swu=$DEPLOY_DIR/qemux86-64/eicke-update-image-qemux86-64.rootfs.swu
imx_swu=$DEPLOY_DIR/qemuimx8mp-evk/eicke-update-image-dev-qemuimx8mp-evk.rootfs.swu
imx_run=$TESTDIR/../eicke-runqemu-imx8mp.sh

imx8mp() {
    # Two boots sharing one disk overlay: slot A installs the bundle, slot B
    # (picked by the wrapper, QEMU has no U-Boot here) must confirm it.
    local ov
    ov=$(mktemp "$BUILDDIR/imx8mp-reg-XXXXXX.qcow2")
    rm -f "$ov"
    qdrive ab_imx8mp_slot_a.json env IMAGE=eicke-image-dev KEEP=1 OVERLAY="$ov" \
        M7LOG="$BUILDDIR/m7-uart4-slot-a.log" \
        "QEMU_EXTRA=-drive\ file=$imx_swu,if=sd,bus=1,format=raw,readonly=on" \
        bash "$imx_run" &&
        qdrive ab_imx8mp_slot_b.json env IMAGE=eicke-image-dev SLOT=rootfs_b \
            KEEP=1 OVERLAY="$ov" M7LOG="$BUILDDIR/m7-uart4-slot-b.log" \
            bash "$imx_run"
    local rc=$?
    rm -f "$ov"
    return $rc
}

failed=0
for t in $tests; do
    case $t in
    arm-ab)
        qdrive ab_test_arm.json runqemu qemuarm-uboot eicke-image wic nographic \
            slirp snapshot "qemuparams=\"-drive file=$arm_swu,if=virtio,format=raw\"" ;;
    arm-lockdown)
        qdrive ab_test_arm_lockdown.json runqemu qemuarm-uboot eicke-image wic \
            nographic slirp snapshot ;;
    x86-ab)
        qdrive ab_test_x86.json runqemu qemux86-64 eicke-image wic ovmf nographic \
            kvm slirp snapshot "qemuparams=\"-drive file=$x86_swu,if=virtio,format=raw\"" ;;
    imx8mp)
        imx8mp ;;
    prod-ab)
        if [ $build = 1 ]; then
            bash "$TESTDIR/prod_ab_test.sh" --build
        else
            bash "$TESTDIR/prod_ab_test.sh"
        fi ;;
    *)
        echo "unknown test: $t" >&2
        false ;;
    esac > "$BUILDDIR/regression-$t.out" 2>&1
    rc=$?
    echo "REG $t rc=$rc"
    [ $rc = 0 ] || failed=1
done
exit $failed
