SUMMARY = "Locate the eicke A/B partitions on the boot disk"
DESCRIPTION = "eicke-bootpart resolves esp/rootfs_a/rootfs_b/data on the disk \
holding the running root filesystem (not by GPT name alone, which can match a \
second disk), and a udev rule exposes them as /dev/eicke/<name> for fstab and \
the SWUpdate A/B hook."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://eicke-bootpart \
    file://61-eicke-bootpart.rules \
"
S = "${UNPACKDIR}"

inherit allarch

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/eicke-bootpart ${D}${libexecdir}/eicke-bootpart

    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${UNPACKDIR}/61-eicke-bootpart.rules ${D}${nonarch_base_libdir}/udev/rules.d/61-eicke-bootpart.rules
}

FILES:${PN} = " \
    ${libexecdir}/eicke-bootpart \
    ${nonarch_base_libdir}/udev/rules.d/61-eicke-bootpart.rules \
"
