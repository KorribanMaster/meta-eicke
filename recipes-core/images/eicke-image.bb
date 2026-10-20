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
    eicke-hwrevision \
    kernel-image \
    e2fsprogs-mke2fs \
    util-linux-blkid \
    libgcc \
    kernel-modules \
    eicke-network \
    eicke-bootconfirm \
    eicke-bootpart \
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

# QEMU i.MX 8M Plus: the RustAmp Cortex-M7 firmware for remoteproc.
IMAGE_INSTALL:append:qemuimx8mp-evk = " rustamp-m7-echo"

# Digi ccimx8mn-dvk: the M7 firmware, and the DTB + overlays in each slot's
# /boot, where U-Boot's compiled-in bootcmd loads them from (per-slot, like
# the kernel).
IMAGE_INSTALL:append:ccimx8mn-dvk = " rustamp-m7-echo kernel-devicetree"
# ALINX AX7015B: the RustAmp Cortex-A9 firmware for CPU1 (pulls in the
# a9-rproc driver), and the DTB in each slot's /boot, like ccimx8mn-dvk.
IMAGE_INSTALL:append:ax7015b = " rustamp-a9-echo kernel-devicetree"
# Trenz TE0820: the RustAmp firmware for both Cortex-R5Fs, and the DTB in
# each slot's /boot, like ccimx8mn-dvk.
IMAGE_INSTALL:append:te0820 = " rustamp-r5-echo kernel-devicetree"

# Bootloader-specific half of the A/B scheme (env seeding, env tooling, boot
# script/config wiring). x86/EFI machines default to GRUB; qemuarm-uboot sets
# EICKE_BOOTLOADER = "u-boot" in its machine conf.
EICKE_BOOTLOADER ??= "grub-efi"
require recipes-core/images/eicke-ab-${EICKE_BOOTLOADER}.inc

# Mount the ESP at /boot (so swupdate + the confirm service can read/write the
# grubenv) and the data partition, in the image's OWN fstab. This must live in
# the rootfs (not wic's per-partition fstab patch) so EVERY A/B slot has it —
# including images swupdate writes raw to a standby slot. Mount via
# /dev/eicke/<name> (eicke-bootpart's udev links: the partition of that GPT
# name on the disk we booted from), not by LABEL= or PARTLABEL=, which can
# match a same-named partition on a second disk (e.g. an eMMC install).
fstab_add_eicke_mounts() {
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/fstab <<EOF
/dev/eicke/esp       /boot                vfat       defaults,sync         0  2
/dev/eicke/data      /data                ext4       defaults              0  2
EOF
}
ROOTFS_POSTPROCESS_COMMAND += "fstab_add_eicke_mounts;"
# ...and keep wic from appending its own UUID= lines for the partitions
# with a mountpoint (/boot, /data) to the rootfs fstab: they would
# duplicate the mounts above. A part's --no-fstab-update does not prevent
# that, it only keeps the updated fstab out of that partition.
WIC_CREATE_EXTRA_ARGS += "--no-fstab-update"
