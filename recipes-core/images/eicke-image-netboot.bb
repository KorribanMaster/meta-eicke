SUMMARY = "Eicke netboot image: iPXE-only disk (UEFI A/B) + RAM rootfs + SWUpdate for iPXE"
DESCRIPTION = "Variant of eicke-image where iPXE is the bootloader and the OS \
is netbooted: the .wic disk carries only iPXE on the ESP plus a data \
partition; the kernel and this image's cpio.gz (served by the netboot \
infrastructure) run entirely from RAM. SWUpdate is kept in the image but \
updates iPXE itself, failsafe via UEFI BootNext A/B (see eicke-ipxeconfirm \
and eicke-update-image-netboot)."
LICENSE = "MIT"

# iPXE/UEFI netboot is x86-only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

require recipes-core/images/core-image-minimal.bb

IMAGE_FEATURES += "ssh-server-openssh"

# SWUpdate + the UEFI tooling its iPXE A/B scheme needs (efibootmgr for
# BootNext/BootOrder, blkid to find the ESP), the confirm service, and basic
# filesystem utilities. kernel-modules stays: the netbooted kernel is the
# machine's bzImage from the same deploy dir, and e.g. vfat/virtio may be
# built as modules that the rootfs must provide. No kernel-image (the kernel
# is served by the netboot server, not stored in the rootfs), no grub, no
# zynq packages.
IMAGE_INSTALL:append = " \
    swupdate \
    swupdate-www \
    swupdate-public-key \
    efibootmgr \
    eicke-ipxeconfirm \
    eicke-network \
    kernel-modules \
    e2fsprogs-mke2fs \
    dosfstools \
    util-linux-blkid \
    libgcc \
"

# Artifacts: the .wic disk (iPXE ESP + data) and the cpio.gz that the netboot
# server serves as the initramfs. local.conf appends "wic wic.bmap ext4";
# drop only ext4 (a raw rootfs of a netboot image has no consumer).
IMAGE_FSTYPES += "cpio.gz"
IMAGE_FSTYPES:remove = "ext4"

# Netboot disk layout (recipe-level override, same pattern as eicke-image-prod).
WKS_FILE = "eicke-netboot.wks"

# ESP contents: one iPXE binary under three names -- the firmware fallback
# path (used on the very first boot, before eicke-ipxeconfirm has created the
# NVRAM entries) and the two A/B slots.
IMAGE_BOOT_FILES = " \
    ipxe.efi;EFI/BOOT/BOOTX64.EFI \
    ipxe.efi;EFI/BOOT/ipxe-a.efi \
    ipxe.efi;EFI/BOOT/ipxe-b.efi \
"
do_image_wic[depends] += "ipxe:do_deploy"

# Mount the ESP at /boot (so swupdate + eicke-ipxeconfirm can manage the iPXE
# slots) and the data partition, in the image's OWN fstab (pattern from
# eicke-image.bb). Mount by filesystem LABEL (set by the .wks). nofail: a
# netbooted system must still reach multi-user if the disk is absent/foreign.
fstab_add_eicke_netboot_mounts() {
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/fstab <<EOF
LABEL=esp            /boot                vfat       defaults,sync,nofail  0  2
LABEL=data           /data                ext4       defaults,nofail       0  2
EOF
}
ROOTFS_POSTPROCESS_COMMAND += "fstab_add_eicke_netboot_mounts;"
