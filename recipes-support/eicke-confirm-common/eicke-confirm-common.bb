SUMMARY = "Shared helpers for the eicke A/B boot-confirmation services"
DESCRIPTION = "Sourced shell library (eicke-confirm-lib.sh) with the common \
logging + trial-gate lifecycle shared by eicke-bootconfirm (grubenv/uboot.env) \
and eicke-ipxeconfirm (UEFI BootOrder). One confirm lifecycle, three backends."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://eicke-confirm-lib.sh"

inherit allarch

do_install() {
    install -d ${D}${libexecdir}
    install -m 0644 ${UNPACKDIR}/eicke-confirm-lib.sh ${D}${libexecdir}/eicke-confirm-lib.sh
}

FILES:${PN} = "${libexecdir}/eicke-confirm-lib.sh"
