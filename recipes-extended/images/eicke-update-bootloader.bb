SUMMARY = "SWUpdate .swu bundle that updates the bootloader on the ESP (te0820)"
DESCRIPTION = "Replaces boot.bin (SPL + PMU firmware) and u-boot.itb (TF-A + \
U-Boot) on the ESP. The bootloader exists only once, unlike the A/B rootfs \
slots, so it gets a bundle of its own: rootfs updates never touch it. The \
files are written next to the current ones, then swapped in by the bundle's \
script, keeping the previous pair as *.old."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "te0820"

inherit swupdate

# Signed with the same key as the rootfs bundles.
require swupdate-signing.inc

# boot.bin and u-boot.itb as u-boot deploys them (no machine suffix).
SWUPDATE_IMAGES = "boot.bin u-boot.itb"

SRC_URI = " \
    file://sw-description \
    file://eicke-bootloader-swap.sh \
"

do_swuimage[depends] += "virtual/bootloader:do_deploy"
