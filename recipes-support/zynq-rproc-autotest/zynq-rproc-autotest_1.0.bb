SUMMARY = "Boot-time auto-runner for the zynq-rproc ptest (headless capture)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
SRC_URI = " \
    file://zynq-rproc-autotest \
    file://zynq-rproc-autotest.service \
"
RDEPENDS:${PN} = "ptest-runner zynq-rproc-test-ptest"

inherit systemd

SYSTEMD_SERVICE:${PN} = "zynq-rproc-autotest.service"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/zynq-rproc-autotest ${D}${libexecdir}/zynq-rproc-autotest

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/zynq-rproc-autotest.service ${D}${systemd_system_unitdir}/zynq-rproc-autotest.service
}

FILES:${PN} = " \
    ${libexecdir}/zynq-rproc-autotest \
    ${systemd_system_unitdir}/zynq-rproc-autotest.service \
"
