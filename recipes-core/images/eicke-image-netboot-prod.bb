SUMMARY = "Eicke netboot production image: Secure-Boot signed iPXE + signed UKI, hardened"
DESCRIPTION = "Production variant of eicke-image-netboot. The firmware (UEFI db) \
verifies iPXE directly (no shim/SELoader -- the UEFI A/B BootNext entries point \
straight at ipxe-a.efi/ipxe-b.efi); iPXE chains a signed UKI (bzImage + this \
image's cpio.gz + fixed cmdline, see eicke-netboot-prod-uki) that the firmware's \
LoadImage verifies. A custom /init mounts the plain /data partition and lays a \
persistent /etc overlay on it before systemd starts. Credentials/SSH hardening \
shared with eicke-image-prod (eicke-prod-hardening.inc). Build with \
EICKE_SECURE_BOOT=1 (see build-netboot-prod.sh)."
LICENSE = "MIT"

# iPXE/UEFI netboot is x86-only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

require recipes-core/images/eicke-image-netboot.bb
require recipes-core/images/eicke-prod-hardening.inc

# Only meaningful with the signing infrastructure active: the deployed
# ipxe.efi must be db-signed and the UKI recipe needs DB.key.
python () {
    if not bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', True, False, d):
        raise bb.parse.SkipRecipe("eicke-image-netboot-prod requires EICKE_SECURE_BOOT=1 (efi-secure-boot)")
}

# /init wrapper: /data mount + /etc overlay before systemd. The cpio image
# type only adds its /init -> /sbin/init symlink when /init is absent
# (image_types.bbclass IMAGE_CMD:cpio), so the installed wrapper wins.
IMAGE_INSTALL:append = " eicke-netboot-prod-init"

# Deliberately kept from the base (unlike eicke-image-prod's minimization):
# kernel-modules -- the exact runtime module set of a netbooted system is
# unproven; on qemux86-64 vfat/ext4/overlay/virtio are =y, so trimming is a
# possible follow-up. NO read-only-rootfs either: the rootfs is RAM, rebuilt
# from the signed UKI on every boot -- integrity comes from the UKI signature
# and persistence from the /etc overlay; an ro-mount would add nothing.

# LockDown.efi (efitools, only deployed under efi-secure-boot): first-time
# PK/KEK/DB enrollment helper for real hardware in firmware Setup Mode, run
# from the firmware boot menu / EFI shell. Inert once keys are enrolled.
IMAGE_BOOT_FILES:append = " LockDown.efi;EFI/BOOT/LockDown.efi"
do_image_wic[depends] += "efitools:do_deploy"

# The base netboot fstab mounts LABEL=data at /data; the /init wrapper already
# did that (the /etc overlay upper lives there), so drop just the data line to
# avoid a second mount. The LABEL=esp /boot line stays (eicke-ipxeconfirm and
# swupdate need it; RequiresMountsFor=/boot). Runs after the base's
# fstab_add_eicke_netboot_mounts (same pattern as eicke_prod_drop_data_fstab).
eicke_netboot_prod_drop_data_fstab() {
    sed -i '/^LABEL=data[[:space:]]/d' ${IMAGE_ROOTFS}${sysconfdir}/fstab
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_netboot_prod_drop_data_fstab;"
