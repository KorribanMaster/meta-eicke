SUMMARY = "Basic Eicke image (core-image-minimal based) with A/B boot + SWUpdate"
LICENSE = "MIT"

require recipes-core/images/core-image-minimal.bb

IMAGE_FEATURES += "ssh-server-openssh"

# SWUpdate, the kernel inside the rootfs (so an A/B rootfs update also updates
# the kernel), and basic filesystem/partition utilities used during updates.
# The bootloader-specific pieces live in the eicke-ab-*.inc required below.
IMAGE_INSTALL:append = " \
    swupdate \
    swupdate-www \
    swupdate-public-key \
    kernel-image \
    e2fsprogs-mke2fs \
    util-linux-blkid \
    libgcc \
    kernel-modules \
    eicke-network \
    eicke-bootconfirm \
    python3 \
    python3-modules \
"

# x86-board-specific bits: the Realtek NIC firmware and the Zynq-RTU-over-PCIe
# host driver + firmware only make sense on the x86 machines.
IMAGE_INSTALL:append:x86-64 = " \
    linux-firmware-rtl8168 \
    zynq-pcie-rproc \
    zynq-rtu-firmware \
"

# Bootloader-specific half of the A/B scheme (env seeding, env tooling, boot
# script/config wiring). x86/EFI machines default to GRUB; qemuarm-uboot sets
# EICKE_BOOTLOADER = "u-boot" in its machine conf.
EICKE_BOOTLOADER ??= "grub-efi"
require recipes-core/images/eicke-ab-${EICKE_BOOTLOADER}.inc

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
