SUMMARY = "SWUpdate .swu update bundle for eicke-image (A/B dual-copy)"
LICENSE = "MIT"
# Recipe fetches files (the sw-description) via SRC_URI, so QA requires license
# checksum info. Reference the common MIT license.
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit swupdate

# The rootfs that gets shipped inside the .swu, and the format SWUpdate writes
# to the standby partition (raw ext4 image). The deployed rootfs is named
# "<image>-<machine>.rootfs.ext4" (the ".rootfs" infix has been the IMAGE_NAME
# scheme since scarthgap and is unchanged in wrynose 6.0), so the fstype flag
# must include the ".rootfs" infix for the class to find it in DEPLOY_DIR_IMAGE.
SWUPDATE_IMAGES = "eicke-image"
SWUPDATE_IMAGES_FSTYPES[eicke-image] = ".rootfs.ext4"

# sw-description (+ optional embedded lua) is taken from SRC_URI by the class.
SRC_URI = "file://sw-description"

# Build the update bundle only after the base image exists.
do_swuimage[depends] += "eicke-image:do_image_complete"
