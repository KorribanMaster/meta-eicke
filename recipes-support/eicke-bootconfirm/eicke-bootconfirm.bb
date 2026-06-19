SUMMARY = "Confirm a healthy boot for the SWUpdate A/B scheme"
DESCRIPTION = "systemd oneshot service that clears the swupdate trial state \
(ustate/bootcount) in the GRUB environment once userspace is up, closing the \
A/B rollback loop so an accepted slot persists across reboots."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://eicke-bootconfirm \
    file://eicke-bootconfirm.service \
"

RDEPENDS:${PN} = "grub-editenv"

inherit systemd

SYSTEMD_SERVICE:${PN} = "eicke-bootconfirm.service"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/eicke-bootconfirm ${D}${libexecdir}/eicke-bootconfirm

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/eicke-bootconfirm.service ${D}${systemd_system_unitdir}/eicke-bootconfirm.service
}

FILES:${PN} = " \
    ${libexecdir}/eicke-bootconfirm \
    ${systemd_system_unitdir}/eicke-bootconfirm.service \
"
