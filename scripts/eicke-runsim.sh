#!/usr/bin/env bash
# Boot an eicke image under the HOST QEMU, wired to the Zynq RTU vfio-user
# device model (the zynq-rtu-vfu-native recipe), with no real board.
#
# Why the host QEMU and not `runqemu`? runqemu launches the Yocto-built
# qemu-system-native, and oe-core does not enable the `vfio-user-pci` *client*
# in it (even on wrynose's QEMU 10.2). The host QEMU (>= 10.1, e.g. 11.x) does,
# so we reproduce what `runqemu eicke-image wic ovmf nographic slirp` would do
# under the host hypervisor and attach the device. Everything else (image,
# kernel, driver, firmware, the device model) is the normal Yocto output.
#
# Run from the Yocto build env (after `. ./setup-environment`), so `oe-run-native`
# and $BUILDDIR are available. Pre-build once:  bitbake zynq-rtu-vfu-native
#
# Env overrides:
#   IMAGE   image link basename   (default: eicke-image-${MACHINE_DIR}.rootfs)
#   MACHINE_DIR  deploy machine dir (default: qemux86-64)
#   DEPLOY  deploy/images dir      (default: $BUILDDIR/tmp/deploy/images/$MACHINE_DIR)
#   QEMU    host qemu binary       (default: qemu-system-x86_64, must have vfio-user-pci)
#   MEM     guest RAM (MiB)        (default: 512)
#   SSH     host port fwd to :22   (default: 2222)
#   ACCEL   kvm|tcg                (default: kvm)
#   SOCK    vfio-user socket       (default: /tmp/zynq-rtu.sock)
set -euo pipefail

SOCK="${SOCK:-/tmp/zynq-rtu.sock}"
QEMU="${QEMU:-qemu-system-x86_64}"
MEM="${MEM:-512}"
SSH="${SSH:-2222}"
ACCEL="${ACCEL:-kvm}"
MACHINE_DIR="${MACHINE_DIR:-qemux86-64}"
IMAGE="${IMAGE:-eicke-image-${MACHINE_DIR}.rootfs}"
DEPLOY="${DEPLOY:-${BUILDDIR:-$PWD}/tmp/deploy/images/${MACHINE_DIR}}"

command -v oe-run-native >/dev/null || { echo "source ./setup-environment first" >&2; exit 1; }

WIC="$DEPLOY/$IMAGE.wic"
OVMF_CODE="$DEPLOY/ovmf.code.qcow2"
OVMF_VARS="$DEPLOY/ovmf.vars.qcow2"
for f in "$WIC" "$OVMF_CODE" "$OVMF_VARS"; do
    [ -f "$f" ] || { echo "missing: $f (bitbake the image and ovmf first)" >&2; exit 1; }
done

# Host QEMU must provide the vfio-user client.
"$QEMU" -device help 2>/dev/null | grep -q vfio-user-pci || {
    echo "$QEMU has no vfio-user-pci (need host QEMU >= 10.1)" >&2; exit 1; }

# Writable per-run copy of the OVMF vars (don't mutate the deployed pflash).
VARS=$(mktemp /tmp/eicke-ovmf-vars.XXXXXX.qcow2)
cp "$OVMF_VARS" "$VARS"

rm -f "$SOCK"
echo "[runsim] starting Yocto-built device model on $SOCK"
oe-run-native zynq-rtu-vfu-native zynq_rtu_vfu "$SOCK" >/tmp/zynq_rtu_vfu.log 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true; pkill -f "zynq_rtu_vfu $SOCK" 2>/dev/null || true; rm -f "$SOCK" "$VARS"' EXIT
for _ in $(seq 1 300); do [ -S "$SOCK" ] && break; sleep 0.1; done
[ -S "$SOCK" ] || { echo "device model did not create $SOCK; see /tmp/zynq_rtu_vfu.log" >&2; tail -20 /tmp/zynq_rtu_vfu.log >&2; exit 1; }

DEV="{\"driver\":\"vfio-user-pci\",\"socket\":{\"path\":\"$SOCK\",\"type\":\"unix\"}}"
echo "[runsim] booting $IMAGE under $("$QEMU" --version | head -1)"
echo "[runsim] ssh: ssh -p $SSH root@localhost   (then: ptest-runner zynq-rproc-test)"
exec "$QEMU" \
    -machine q35,i8042=off,accel="$ACCEL" -cpu IvyBridge -smp 4 -m "$MEM" \
    -drive file="$OVMF_CODE",if=pflash,format=qcow2,readonly=on \
    -drive file="$VARS",if=pflash,format=qcow2 \
    -drive file="$WIC",if=virtio,format=raw,snapshot=on \
    -device "$DEV" \
    -netdev user,id=net0,hostfwd=tcp::"$SSH"-:22 \
    -device virtio-net-pci,netdev=net0 \
    -object rng-random,filename=/dev/urandom,id=rng0 -device virtio-rng-pci,rng=rng0 \
    -serial mon:stdio -nographic
