SUMMARY = "SWUpdate .swu bundle: failsafe A/B update of the db-SIGNED iPXE (netboot-prod)"
DESCRIPTION = "netboot-prod variant of eicke-update-image-netboot: ships the \
Secure-Boot db-signed ipxe.efi (ipxe_git.bb signs the deployed binary under \
efi-secure-boot) and reuses the exact same staging + one-shot UEFI BootNext \
postinstall flow and rollback semantics. Under Secure Boot the failsafe is \
doubly enforced: a corrupted or tampered standby slot also fails the firmware \
signature check and falls through BootOrder. The OS itself needs no on-device \
update path -- it is the signed UKI served by the netboot server."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit swupdate
require swupdate-signing.inc

python () {
    if not bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', True, False, d):
        raise bb.parse.SkipRecipe("eicke-update-image-netboot-prod requires EICKE_SECURE_BOOT=1 (efi-secure-boot)")
}

# The deployed iPXE binary -- already db-signed in this build (see ipxe_git.bb).
SWUPDATE_IMAGES = "ipxe"
SWUPDATE_IMAGES_FSTYPES[ipxe] = ".efi"
SWUPDATE_IMAGES_NOAPPEND_MACHINE[ipxe] = "1"

# Reuse the netboot bundle's sw-description + postinstall verbatim: slot
# selection, staged write and BootNext arming are identical for prod.
FILESEXTRAPATHS:prepend := "${THISDIR}/eicke-update-image-netboot:"
SRC_URI = " \
    file://sw-description \
    file://ipxe-postinst.sh \
"

do_swuimage[depends] += "ipxe:do_deploy"
