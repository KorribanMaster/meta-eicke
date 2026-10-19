#!/usr/bin/env bash
# Boot an eicke A/B wic on QEMU imx8mp-evk (qemuimx8mp-evk machine), with the
# Cortex-M7 available to Linux remoteproc.
#
# Why not `runqemu`? The EVK's SD slot is an on-board uSDHC, which needs
# `-drive if=sd,bus=2`; runqemu's wic path only knows virtio/scsi/ide/sdhci-pci
# drives. And there is no bootloader to emulate: QEMU imx8mp-evk cannot run
# U-Boot (no SPL/DDR init model), so the kernel is booted directly and the A/B
# slot is picked here (SLOT=rootfs_a|rootfs_b) instead of by a boot script.
# No automatic rollback: an unconfirmed slot B is booted again until you pick A.
#
# Uses the Yocto-built (Cortex-M7 AMP patched) qemu-system-aarch64 from the
# qemu-helper-native sysroot recorded in the image's qemuboot.conf. Run inside
# the build container from the build dir (after sourcing the init script).
#
# Env overrides:
#   IMAGE      image basename          (default: eicke-image)
#   DEPLOY     deploy/images dir       (default: $BUILDDIR/tmp/deploy/images/qemuimx8mp-evk)
#   SLOT       rootfs_a | rootfs_b     (default: rootfs_a)
#   MEM        guest RAM               (default: 3G)
#   SSH        host port fwd to :22    (default: 2222)
#   M7LOG      M7 UART4 capture file   (default: ./m7-uart4.log)
#   OVERLAY    qcow2 disk overlay      (default: a temp file, deleted on exit)
#   KEEP=1     keep OVERLAY so a later run sees the guest's writes (A/B tests)
#   SWU        a .swu to attach as a second SD card (the guest sees it as the
#              mmcblk without partitions: swupdate -i /dev/mmcblkX); read-only
#              for the guest's purposes, writes go to a temporary overlay
#   QEMU_EXTRA extra qemu args
set -euo pipefail

IMAGE="${IMAGE:-eicke-image}"
MACHINE_DIR=qemuimx8mp-evk
DEPLOY="${DEPLOY:-${BUILDDIR:-$PWD}/tmp/deploy/images/${MACHINE_DIR}}"
SLOT="${SLOT:-rootfs_a}"
MEM="${MEM:-3G}"
SSH="${SSH:-2222}"
M7LOG="${M7LOG:-$PWD/m7-uart4.log}"

case "$SLOT" in
    rootfs_a|rootfs_b) ;;
    *) echo "SLOT must be rootfs_a or rootfs_b" >&2; exit 1 ;;
esac

BASE="$DEPLOY/$IMAGE-$MACHINE_DIR.rootfs"
CONF="$BASE.qemuboot.conf"
WIC="$BASE.wic"
for f in "$CONF" "$WIC"; do
    [ -f "$f" ] || { echo "missing: $f (bitbake $IMAGE first)" >&2; exit 1; }
done

# qemuboot.conf stores paths relative to the deploy dir. Its qb_default_kernel
# can be an unexpanded ${@...} expression, so use the KERNEL_IMAGETYPE link.
conf() { sed -n "s/^$1 = //p" "$CONF"; }
BINDIR="$DEPLOY/$(conf staging_bindir_native)"
KERNEL="$DEPLOY/Image"
DTB="$DEPLOY/$(conf qb_dtb)"
QEMU="$BINDIR/qemu-system-aarch64"
QEMU_IMG="$BINDIR/qemu-img"
[ -x "$QEMU_IMG" ] || QEMU_IMG="$(command -v qemu-img || true)"
for f in "$QEMU" "$QEMU_IMG" "$KERNEL" "$DTB"; do
    [ -e "$f" ] || { echo "missing: $f (bitbake qemu-helper-native?)" >&2; exit 1; }
done

# The emulated SD card must be a power of two in size (the A/B wic is ~6.1 GiB),
# so boot from a larger qcow2 overlay backed by the read-only wic. This also
# keeps the deployed wic pristine (like runqemu's `snapshot`).
# Temporary files removed on exit (the qemu run below is not exec'd).
CLEANUP=()
trap 'rm -f "${CLEANUP[@]}"' EXIT
if [ -z "${OVERLAY:-}" ]; then
    OVERLAY="$(mktemp /tmp/eicke-imx8mp-XXXXXX.qcow2)"
    [ "${KEEP:-0}" = 1 ] || CLEANUP+=("$OVERLAY")
fi
if [ ! -s "$OVERLAY" ]; then
    size=$(stat -L -c %s "$WIC")
    sd=1; while [ "$sd" -lt "$size" ]; do sd=$((sd * 2)); done
    "$QEMU_IMG" create -q -f qcow2 -b "$(readlink -f "$WIC")" -F raw "$OVERLAY" "$sd"
fi
[ "${KEEP:-0}" = 1 ] && echo "[runqemu-imx8mp] disk overlay kept: $OVERLAY (pass OVERLAY= to reuse)"

# Same power-of-two rule for the .swu card. It needs its own drive id: QEMU
# names every if=sd drive sd<unit>, which clashes with the boot card's "sd0".
swu_drive=()
if [ -n "${SWU:-}" ]; then
    SWU_OVERLAY="$(mktemp /tmp/eicke-imx8mp-swu-XXXXXX.qcow2)"
    CLEANUP+=("$SWU_OVERLAY")
    size=$(stat -L -c %s "$SWU")
    sd=1; while [ "$sd" -lt "$size" ]; do sd=$((sd * 2)); done
    "$QEMU_IMG" create -q -f qcow2 -b "$(readlink -f "$SWU")" -F raw "$SWU_OVERLAY" "$sd"
    swu_drive=(-drive "file=$SWU_OVERLAY,if=sd,bus=1,id=swu,format=qcow2")
fi

# Root by the slot partition's PARTUUID (read from the wic's GPT; the overlay
# only adds writes on top), as the real bootloaders do: a GPT name like
# rootfs_a may repeat on another disk. eicke.slot= names the slot for userspace.
case "$SLOT" in rootfs_a) PARTNO=2 ;; *) PARTNO=3 ;; esac
PARTUUID="$(python3 -I - "$WIC" "$PARTNO" <<'PY'
import struct, sys, uuid
with open(sys.argv[1], "rb") as f:
    f.seek(512)
    hdr = f.read(92)
    assert hdr[:8] == b"EFI PART", "no GPT"
    lba, _, size = struct.unpack_from("<QII", hdr, 72)
    f.seek(lba * 512 + (int(sys.argv[2]) - 1) * size)
    print(uuid.UUID(bytes_le=f.read(size)[16:32]))
PY
)"

echo "[runqemu-imx8mp] $IMAGE on $SLOT; M7 UART4 -> $M7LOG; ssh -p $SSH root@127.0.0.1"
# Serial: UART1 null, UART2 = A53 console, UART3 null, UART4 = M7 log.
# Default -smp (4 A53 + the M7) comes from the patched machine. Not exec'd, so
# the EXIT trap still removes the temporary overlays.
"$QEMU" -M imx8mp-evk -m "$MEM" -display none \
    -kernel "$KERNEL" -dtb "$DTB" \
    -append "root=PARTUUID=$PARTUUID eicke.slot=$SLOT rootwait console=ttymxc1,115200" \
    -drive "file=$OVERLAY,if=sd,bus=2,format=qcow2" \
    -nic "user,model=imx.enet,hostfwd=tcp::$SSH-:22" \
    -serial null -serial mon:stdio -serial null -serial "file:$M7LOG" \
    "${swu_drive[@]}" ${QEMU_EXTRA:-}
