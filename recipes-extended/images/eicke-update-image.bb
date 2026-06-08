SUMMARY = "SWUpdate .swu update bundle for eicke-image (A/B dual-copy)"
LICENSE = "MIT"

inherit swupdate

# The rootfs that gets shipped inside the .swu, and the format SWUpdate writes
# to the standby partition (raw ext4 image).
SWUPDATE_IMAGES = "eicke-image"
SWUPDATE_IMAGES_FSTYPES[eicke-image] = ".ext4"

# sw-description (+ optional embedded lua) is taken from SRC_URI by the class.
SRC_URI = "file://sw-description"

# Build the update bundle only after the base image exists.
do_swuimage[depends] += "eicke-image:do_image_complete"
