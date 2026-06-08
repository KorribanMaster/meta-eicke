SUMMARY = "Confirm a healthy boot for the SWUpdate A/B scheme"
DESCRIPTION = "sysvinit service that clears the swupdate trial state \
(ustate/bootcount) in the GRUB environment once userspace is up, closing the \
A/B rollback loop so an accepted slot persists across reboots."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://eicke-bootconfirm.init"

RDEPENDS:${PN} = "grub-editenv"

inherit update-rc.d

INITSCRIPT_NAME = "eicke-bootconfirm"
# Run late in boot, after filesystems/services are up.
INITSCRIPT_PARAMS = "defaults 99"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/eicke-bootconfirm.init ${D}${sysconfdir}/init.d/eicke-bootconfirm
}

FILES:${PN} = "${sysconfdir}/init.d/eicke-bootconfirm"
