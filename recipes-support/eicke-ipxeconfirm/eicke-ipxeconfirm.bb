SUMMARY = "Manage/confirm the iPXE A/B UEFI boot entries (netboot image)"
DESCRIPTION = "systemd oneshot service for the eicke netboot image's failsafe \
iPXE A/B scheme (UEFI BootNext). On first boot it creates the ipxe-a/ipxe-b \
UEFI boot entries pointing at the two iPXE copies on the ESP. On every boot it \
promotes the currently running slot to the front of BootOrder — after swupdate \
armed a one-shot BootNext trial of the standby slot, reaching userspace on \
that slot is the confirmation that makes it permanent; if the trial slot never \
boots, the next reset falls back to the old BootOrder automatically."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://eicke-ipxeconfirm \
    file://eicke-ipxeconfirm.service \
"

RDEPENDS:${PN} = "efibootmgr util-linux-blkid eicke-confirm-common"

inherit systemd

SYSTEMD_SERVICE:${PN} = "eicke-ipxeconfirm.service"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/eicke-ipxeconfirm ${D}${libexecdir}/eicke-ipxeconfirm

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/eicke-ipxeconfirm.service ${D}${systemd_system_unitdir}/eicke-ipxeconfirm.service
}

FILES:${PN} = " \
    ${libexecdir}/eicke-ipxeconfirm \
    ${systemd_system_unitdir}/eicke-ipxeconfirm.service \
"
