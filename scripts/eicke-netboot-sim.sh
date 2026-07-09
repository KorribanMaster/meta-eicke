#!/usr/bin/env bash
# Boot eicke-image-netboot under the Yocto-built QEMU (runqemu), with the
# host side of the netboot contract set up: a boot.ipxe handed out as the
# DHCP bootfile via slirp's built-in TFTP server, and kernel + initramfs
# served over HTTP from the deploy dir. OVMF runs with a private, persistent
# vars copy so the UEFI A/B state (BootOrder/BootNext) survives reboots and
# re-runs — required to exercise the iPXE update flow.
#
# Run from the Yocto build env (after `. ./setup-environment`), with KVM
# available (EICKE_DOCKER_QEMU=1 for the container). Pre-build once:
#   bitbake eicke-image-netboot ovmf qemu-system-native qemu-helper-native
#
# Interact:  ssh -p $SSH root@localhost      (or the serial console)
# Update:    curl -F filename=@<...>.swu http://localhost:$SWU/upload
# Reset the A/B state to a fresh first boot:  rm $SIM/ovmf.vars.netboot.qcow2
#
# Env overrides:
#   MACHINE_DIR  deploy machine dir      (default: qemux86-64)
#   DEPLOY  deploy/images dir            (default: $BUILDDIR/tmp/deploy/images/$MACHINE_DIR)
#   SIM     working dir                  (default: $BUILDDIR/netboot-sim)
#   MEM     guest RAM (MiB)              (default: 2048)
#   HTTP    kernel/initramfs HTTP port   (default: 8000)
#   SSH     host port fwd to :22         (default: 2222)
#   SWU     host port fwd to :8080       (default: 8080)
set -euo pipefail

MACHINE_DIR="${MACHINE_DIR:-qemux86-64}"
DEPLOY="${DEPLOY:-${BUILDDIR:-$PWD}/tmp/deploy/images/${MACHINE_DIR}}"
SIM="${SIM:-${BUILDDIR:-$PWD}/netboot-sim}"
MEM="${MEM:-2048}"
HTTP="${HTTP:-8000}"
SSH="${SSH:-2222}"
SWU="${SWU:-8080}"

command -v runqemu >/dev/null || { echo "source ./setup-environment first" >&2; exit 1; }

WIC="$DEPLOY/eicke-image-netboot-${MACHINE_DIR}.rootfs.wic"
CPIO="eicke-image-netboot-${MACHINE_DIR}.rootfs.cpio.gz"
for f in "$WIC" "$DEPLOY/$CPIO" "$DEPLOY/ovmf.code.qcow2" "$DEPLOY/ovmf.vars.qcow2"; do
    [ -f "$f" ] || { echo "missing: $f (bitbake eicke-image-netboot + ovmf first)" >&2; exit 1; }
done

mkdir -p "$SIM"
cat > "$SIM/boot.ipxe" <<EOF
#!ipxe
kernel http://10.0.2.2:${HTTP}/bzImage console=ttyS0 rdinit=/sbin/init
initrd http://10.0.2.2:${HTTP}/${CPIO}
boot
EOF

# Writable OVMF pflash pair. The vars copy is created ONCE and then kept:
# it is the UEFI NVRAM holding the ipxe-a/ipxe-b boot entries. (runqemu
# attaches any argument whose basename starts with "ovmf" as a pflash drive.)
cp -n "$DEPLOY/ovmf.code.qcow2" "$SIM/" 2>/dev/null || true
[ -f "$SIM/ovmf.vars.netboot.qcow2" ] || cp "$DEPLOY/ovmf.vars.qcow2" "$SIM/ovmf.vars.netboot.qcow2"

echo "[netboot-sim] serving $DEPLOY on 127.0.0.1:$HTTP"
python3 -m http.server "$HTTP" --bind 127.0.0.1 --directory "$DEPLOY" >/dev/null 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true' EXIT

echo "[netboot-sim] ssh: ssh -p $SSH root@localhost   swupdate: http://localhost:$SWU"
QB_SLIRP_OPT="-netdev user,id=net0,tftp=$SIM,bootfile=boot.ipxe,hostfwd=tcp:127.0.0.1:${SWU}-:8080,hostfwd=tcp:127.0.0.1:${SSH}-:22" \
exec runqemu "$WIC" slirp kvm nographic \
    "$SIM/ovmf.code.qcow2" "$SIM/ovmf.vars.netboot.qcow2" \
    qemuparams="-m $MEM"
