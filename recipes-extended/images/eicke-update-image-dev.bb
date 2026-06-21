SUMMARY = "SWUpdate .swu update bundle for eicke-image-dev (A/B dual-copy)"
LICENSE = "MIT"
# Recipe fetches files (the sw-description) via SRC_URI, so QA requires license
# checksum info. Reference the common MIT license.
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit swupdate

# Ship the dev rootfs inside the .swu (raw ext4 written to the standby slot).
# Deployed as "eicke-image-dev-<machine>.rootfs.ext4"; the fstype flag must
# include the ".rootfs" infix so the class finds it in DEPLOY_DIR_IMAGE.
SWUPDATE_IMAGES = "eicke-image-dev"
SWUPDATE_IMAGES_FSTYPES[eicke-image-dev] = ".rootfs.ext4"

# sw-description (references the eicke-image-dev rootfs) is taken from SRC_URI.
SRC_URI = "file://sw-description"

# Build the update bundle only after the dev image exists.
do_swuimage[depends] += "eicke-image-dev:do_image_complete"
