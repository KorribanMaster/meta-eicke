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
    grub-editenv \
    kernel-image \
    e2fsprogs-mke2fs \
    util-linux-blkid \
    libgcc \
"

# The ESP holds GRUB + grub.cfg + grubenv and is mounted at /boot so SWUpdate
# (and grub-editenv) can read/write the boot environment. Seed an initial
# grubenv onto the ESP at EFI/BOOT/grubenv (swupdate's GRUB handler won't
# create it) via the grubenv recipe + IMAGE_BOOT_FILES.
# NOTE: the bootimg-efi wic plugin reads IMAGE_EFI_BOOT_FILES (not the
# bootimg-partition IMAGE_BOOT_FILES) for files placed on the ESP.
IMAGE_EFI_BOOT_FILES:append = " grubenv;EFI/BOOT/grubenv"
do_image_wic[depends] += "grubenv:do_deploy"
