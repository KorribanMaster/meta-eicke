SUMMARY = "Basic Eicke image (core-image-minimal based) with GRUB-EFI A/B + SWUpdate"
LICENSE = "MIT"

require recipes-core/images/core-image-minimal.bb

IMAGE_FEATURES += "ssh-server-openssh"

# SWUpdate + the tooling its GRUB handler needs at runtime, the kernel inside
# the rootfs (so an A/B rootfs update also updates the kernel), and basic
# filesystem/partition utilities used during updates.
IMAGE_INSTALL:append = " \
    swupdate \
    swupdate-www \
    swupdate-public-key \
    grub-editenv \
    kernel-image \
    e2fsprogs-mke2fs \
    util-linux-blkid \
    libgcc \
    kernel-modules \
    linux-firmware-rtl8168 \
    eicke-network \
    eicke-bootconfirm \
    zynq-pcie-rproc \
    zynq-rtu-firmware \
"

# The ESP holds GRUB + grub.cfg + grubenv and is mounted at /boot so SWUpdate
# (and grub-editenv) can read/write the boot environment. Seed an initial
# grubenv onto the ESP at EFI/BOOT/grubenv (swupdate's GRUB handler won't
# create it) via the grubenv recipe + IMAGE_BOOT_FILES.
# NOTE: the bootimg-efi wic plugin reads IMAGE_EFI_BOOT_FILES (not the
# bootimg-partition IMAGE_BOOT_FILES) for files placed on the ESP.
# Under efi-secure-boot the grub-efi recipe creates+ships grubenv on the ESP
# itself (and would conflict with this standalone seed), so only use the
# grubenv recipe when secure boot is off (the bootimg-efi flow).
IMAGE_EFI_BOOT_FILES:append = "${@bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', '', ' grubenv;EFI/BOOT/grubenv', d)}"
do_image_wic[depends] += "${@bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', '', 'grubenv:do_deploy', d)}"

# grub.cfg is pulled in at wic runtime via the wks 'bootloader --configfile',
# so bitbake doesn't track it automatically; register it as a task input so
# edits to the A/B boot config actually trigger a wic rebuild.
do_image_wic[file-checksums] += "${THISDIR}/../../files/wic/grub.cfg:True"

# Mount the ESP at /boot (so swupdate + the confirm service can read/write the
# grubenv) and the data partition, in the image's OWN fstab. This must live in
# the rootfs (not wic's per-partition fstab patch) so EVERY A/B slot has it —
# including images swupdate writes raw to a standby slot. Mount by filesystem
# LABEL (set by the .wks --label, stable across A/B raw writes of the rootfs).
fstab_add_eicke_mounts() {
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/fstab <<EOF
LABEL=esp            /boot                vfat       defaults,sync         0  2
LABEL=data           /data                ext4       defaults              0  2
EOF
}
ROOTFS_POSTPROCESS_COMMAND += "fstab_add_eicke_mounts;"
