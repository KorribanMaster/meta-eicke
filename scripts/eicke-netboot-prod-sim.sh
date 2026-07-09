#!/usr/bin/env bash
# Secure-Boot netboot sim: eicke-image-netboot-prod under the Yocto-built QEMU
# (runqemu) with OVMF secure-boot firmware and OUR PK/KEK/DB pre-enrolled into
# a private varstore (virt-fw-vars). The DHCP bootfile hands iPXE a boot.ipxe
# that chains the signed UKI over HTTP; the firmware's LoadImage verifies both
# iPXE (from the ESP A/B slots) and the UKI. See eicke-netboot-sim.sh for the
# non-SB base flow.
#
# Run from the Yocto build env (after `. ./setup-environment`), with KVM
# available. Pre-build once:  ./build-netboot-prod.sh
#
# Interact:  ssh -p $SSH root@localhost      (key auth; prod creds active)
# Update:    curl -F filename=@<...>.swu http://localhost:$SWU/upload
# Reset the A/B + enrollment state to a fresh first boot:
#   rm $SIM/ovmf.vars.sb.qcow2
#
# Env overrides:
#   MACHINE_DIR  deploy machine dir      (default: qemux86-64)
#   DEPLOY  deploy/images dir            (default: $BUILDDIR/tmp/deploy/images/$MACHINE_DIR)
#   SIM     working dir                  (default: $BUILDDIR/netboot-prod-sim)
#   KEYS    UEFI SB key dir              (default: /keys/sb-user/uefi_sb_keys)
#   VFW     virt-fw-vars binary          (default: <workspace>/.vfw-venv/bin/virt-fw-vars)
#   MEM     guest RAM (MiB)              (default: 2048)
#   HTTP    UKI HTTP port                (default: 8000)
#   SSH     host port fwd to :22         (default: 2222)
#   SWU     host port fwd to :8080       (default: 8080)
set -euo pipefail

MACHINE_DIR="${MACHINE_DIR:-qemux86-64}"
DEPLOY="${DEPLOY:-${BUILDDIR:-$PWD}/tmp/deploy/images/${MACHINE_DIR}}"
SIM="${SIM:-${BUILDDIR:-$PWD}/netboot-prod-sim}"
KEYS="${KEYS:-/keys/sb-user/uefi_sb_keys}"
VFW="${VFW:-$(cd "${BUILDDIR:-$PWD}/.." && pwd)/.vfw-venv/bin/virt-fw-vars}"
# Signature owner GUID (UEFI_SIG_OWNER_GUID / VENDOR_UUID, meta-signing-key).
GUID="${GUID:-1f7b9654-2107-4697-8f1c-0cbc38874588}"
MEM="${MEM:-2048}"
HTTP="${HTTP:-8000}"
SSH="${SSH:-2222}"
SWU="${SWU:-8080}"

command -v runqemu >/dev/null || { echo "source ./setup-environment first" >&2; exit 1; }
[ -x "$VFW" ] || { echo "virt-fw-vars not found at $VFW" >&2; exit 1; }

WIC="$DEPLOY/eicke-image-netboot-prod-${MACHINE_DIR}.rootfs.wic"
UKI="eicke-netboot-prod-uki.efi"
for f in "$WIC" "$DEPLOY/$UKI" "$DEPLOY/ovmf.secboot.code.qcow2" "$DEPLOY/ovmf.vars.qcow2" \
         "$KEYS/PK.crt" "$KEYS/KEK.crt" "$KEYS/DB.crt"; do
    [ -f "$f" ] || { echo "missing: $f (run ./build-netboot-prod.sh first / mount /keys)" >&2; exit 1; }
done

mkdir -p "$SIM"
cat > "$SIM/boot.ipxe" <<EOF
#!ipxe
chain http://10.0.2.2:${HTTP}/${UKI}
EOF

# One-time: varstore with OUR PK/KEK/DB enrolled and SecureBoot enabled. Then
# persistent: it also holds the ipxe-a/ipxe-b BootOrder state across reboots
# and re-runs (delete it to simulate a factory-fresh first boot).
if [ ! -f "$SIM/ovmf.vars.sb.qcow2" ]; then
    echo "[netboot-prod-sim] enrolling PK/KEK/DB into a fresh varstore"
    qemu-img convert -f qcow2 -O raw "$DEPLOY/ovmf.vars.qcow2" "$SIM/vars.raw"
    "$VFW" --input "$SIM/vars.raw" --output "$SIM/vars.sb.raw" \
        --secure-boot --no-microsoft \
        --set-pk  "$GUID" "$KEYS/PK.crt" \
        --add-kek "$GUID" "$KEYS/KEK.crt" \
        --add-db  "$GUID" "$KEYS/DB.crt"
    qemu-img convert -f raw -O qcow2 "$SIM/vars.sb.raw" "$SIM/ovmf.vars.sb.qcow2"
    rm -f "$SIM/vars.raw" "$SIM/vars.sb.raw"
fi
cp -n "$DEPLOY/ovmf.secboot.code.qcow2" "$SIM/" 2>/dev/null || true

echo "[netboot-prod-sim] serving $DEPLOY on 127.0.0.1:$HTTP"
python3 -m http.server "$HTTP" --bind 127.0.0.1 --directory "$DEPLOY" >/dev/null 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true' EXIT

echo "[netboot-prod-sim] ssh: ssh -p $SSH root@localhost   swupdate: http://localhost:$SWU"
QB_SLIRP_OPT="-netdev user,id=net0,tftp=$SIM,bootfile=boot.ipxe,hostfwd=tcp:127.0.0.1:${SWU}-:8080,hostfwd=tcp:127.0.0.1:${SSH}-:22" \
exec runqemu "$WIC" slirp kvm nographic \
    "$SIM/ovmf.secboot.code.qcow2" "$SIM/ovmf.vars.sb.qcow2" \
    qemuparams="-m $MEM"
