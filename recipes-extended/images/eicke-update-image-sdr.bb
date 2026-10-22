SUMMARY = "SWUpdate .swu update bundle for eicke-image-sdr (A/B dual-copy)"
LICENSE = "MIT"
# Recipe fetches files (the sw-description) via SRC_URI, so QA requires license
# checksum info. Reference the common MIT license.
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "fishball-sdr"

inherit swupdate

# Sign the bundle (RSA-PSS over sw-description) like the other variants.
require swupdate-signing.inc

# Ship the SDR rootfs inside the .swu (raw ext4 written to the standby slot).
# The slot's /boot carries the kernel, device tree and PL bitstream, so they
# are updated and rolled back together. Deployed as
# "eicke-image-sdr-<machine>.rootfs.ext4"; the fstype flag must include the
# ".rootfs" infix so the class finds it in DEPLOY_DIR_IMAGE.
SWUPDATE_IMAGES = "eicke-image-sdr"
SWUPDATE_IMAGES_FSTYPES[eicke-image-sdr] = ".rootfs.ext4"

# sw-description (references the eicke-image-sdr rootfs) is taken from SRC_URI.
SRC_URI = "file://sw-description"

# Build the update bundle only after the SDR image exists.
do_swuimage[depends] += "eicke-image-sdr:do_image_complete"
