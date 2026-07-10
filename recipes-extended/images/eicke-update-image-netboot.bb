SUMMARY = "SWUpdate .swu bundle: failsafe A/B iPXE bootloader update (UEFI BootNext)"
DESCRIPTION = "Ships a freshly built ipxe.efi for the eicke netboot image. \
The bundle stages the binary on the ESP, and its postinstall script writes it \
to the standby A/B slot and arms a one-shot UEFI BootNext trial; the running \
slot is only replaced in BootOrder after the new iPXE has proven it boots \
(eicke-ipxeconfirm). See eicke-update-image-netboot/sw-description."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# netboot images are x86-only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

inherit swupdate

# Sign the bundle (RSA-PSS over sw-description), same key as the other bundles.
require swupdate-signing.inc

# The deployed iPXE binary (machine-suffix-free "ipxe.efi", see ipxe_git.bb).
SWUPDATE_IMAGES = "ipxe"
SWUPDATE_IMAGES_FSTYPES[ipxe] = ".efi"
SWUPDATE_IMAGES_NOAPPEND_MACHINE[ipxe] = "1"

# sw-description + the postinstall script (both packed into the .swu).
SRC_URI = " \
    file://sw-description \
    file://ipxe-postinst.sh \
"

do_swuimage[depends] += "ipxe:do_deploy"
