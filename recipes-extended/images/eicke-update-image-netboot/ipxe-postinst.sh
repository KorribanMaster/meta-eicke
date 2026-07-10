#!/bin/sh
# SWUpdate shellscript handler entry point for the iPXE A/B update: called
# with "preinst" before and "postinst" after the files section was installed.
# All work happens postinst (the rawfile has been staged on the ESP by then):
# write the staged binary over the STANDBY slot and arm a one-shot BootNext
# trial. The active slot's file and BootOrder are never touched here --
# promotion happens in eicke-ipxeconfirm after the trial slot boots.
[ "${1:-}" = "postinst" ] || exit 0
set -eu

ESP=/boot/EFI/BOOT
STAGED=$ESP/ipxe-staged.efi
[ -f "$STAGED" ] || { echo "ipxe-postinst: staged $STAGED missing" >&2; exit 1; }

# The netboot image's systemd does not mount efivarfs (initramfs root);
# mount it, and remount rw in case it was mounted read-only.
grep -q ' /sys/firmware/efi/efivars ' /proc/mounts || \
    mount -t efivarfs efivarfs /sys/firmware/efi/efivars 2>/dev/null || true
mount -o remount,rw /sys/firmware/efi/efivars 2>/dev/null || true

ebm_out=$(efibootmgr)
entry_num() {  # entry_num <label> -> 4-hex-digit entry number, or empty
    printf '%s\n' "$ebm_out" | sed -n "s/^Boot\([0-9A-F]\{4\}\)\*\{0,1\}[[:space:]]\{1,\}$1[[:space:]].*/\1/p" | head -n1
}
a=$(entry_num ipxe-a); b=$(entry_num ipxe-b)
current=$(printf '%s\n' "$ebm_out" | sed -n 's/^BootCurrent: //p')

if [ -z "$a" ] || [ -z "$b" ]; then
    echo "ipxe-postinst: ipxe-a/ipxe-b UEFI entries missing (eicke-ipxeconfirm never ran?)" >&2
    exit 1
fi

case "$current" in
    "$a") standby_num=$b; standby_file=ipxe-b.efi ;;
    "$b") standby_num=$a; standby_file=ipxe-a.efi ;;
    *)
        # Booted via the BOOTX64.EFI fallback path (or a non-ipxe entry):
        # update the slot that is NOT first in BootOrder so the slot the next
        # regular boot would use stays intact.
        first=$(printf '%s\n' "$ebm_out" | sed -n 's/^BootOrder: \([0-9A-F]\{4\}\).*/\1/p')
        if [ "$first" = "$b" ]; then standby_num=$a; standby_file=ipxe-a.efi
        else standby_num=$b; standby_file=ipxe-b.efi; fi
        ;;
esac

mv -f "$STAGED" "$ESP/$standby_file"
sync

efibootmgr --bootnext "$standby_num" >/dev/null
echo "ipxe-postinst: wrote $standby_file, BootNext=$standby_num (one-shot trial)"
exit 0
