# Eicke A/B boot menu, sourced by grub.cfg AFTER load_env (grub-efi.cfg loads
# grubenv from the ESP). This file is signed by meta-efi-secure-boot and
# verified by grub's embedded key, so the A/B selection logic and the kernel
# command line cannot be tampered with under Secure Boot.
#
# rootdev (rootfs_a|rootfs_b) is persisted in grubenv and flipped by SWUpdate's
# grub handler after writing the standby slot; a bootcount/ustate rollback
# switches back if a freshly-updated image fails to confirm.

# Canonicalize rootdev. grubenv is writable, so treat its content as
# UNTRUSTED data: anything that is not exactly "rootfs_b" (including empty on
# first boot, or a crafted value like "rootfs_a init=/bin/sh") becomes the
# literal "rootfs_a", so no external string can ever reach the kernel command
# line below. Mirrored in recipes-bsp/u-boot/files/boot.cmd — keep in sync.
if [ "${rootdev}" = "rootfs_b" ]; then
    set rootdev="rootfs_b"
else
    set rootdev="rootfs_a"
fi

# Rollback: ustate=1 means "update on trial". Give it one boot; if it comes back
# unconfirmed, switch to the other slot and mark FAILED (ustate=3).
if [ "${ustate}" = "1" ]; then
    if [ "${bootcount}" = "1" ]; then
        if [ "${rootdev}" = "rootfs_a" ]; then
            set rootdev="rootfs_b"
        else
            set rootdev="rootfs_a"
        fi
        set ustate="3"
        save_env rootdev ustate
    else
        set bootcount="1"
        save_env bootcount
    fi
fi

# Map the active slot to its GPT partition number (matches eicke-ab-prod.wks.in:
# p1=esp, p2=rootfs_a, p3=rootfs_b, p4=data).
if [ "${rootdev}" = "rootfs_a" ]; then
    set slot=2
else
    set slot=3
fi

menuentry "Eicke Linux (${rootdev})" --unrestricted {
    savedefault
    # Derive the boot drive from ${root} (set by grub's prefix search on the
    # ESP), then point root at the active slot partition on that same drive and
    # load that slot's kernel. grub verifies /boot/bzImage against its detached
    # signature (/boot/bzImage.sig) using the embedded public key.
    regexp --set=1:bootdrive '^([^,]+),' "${root}"
    set root="${bootdrive},gpt${slot}"
    linux /boot/bzImage root=PARTLABEL=${rootdev} rootwait console=ttyS0,115200 console=tty0
}
