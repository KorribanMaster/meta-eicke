SUMMARY = "Hardware watchdog fed by systemd for eicke images"
DESCRIPTION = "A systemd system.conf drop-in that makes PID 1 open the \
hardware watchdog (/dev/watchdog0) and feed it: if the kernel or systemd \
hangs, the watchdog resets the board, and a trial boot of the A/B scheme that \
hangs before its confirm is rolled back by the bootloader. Only for machines \
whose watchdog resets the whole system (see the machine docs)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-eicke-watchdog.conf"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "systemd"

do_install() {
    install -d ${D}${nonarch_libdir}/systemd/system.conf.d
    install -m 0644 ${UNPACKDIR}/10-eicke-watchdog.conf \
        ${D}${nonarch_libdir}/systemd/system.conf.d/10-eicke-watchdog.conf
}

FILES:${PN} = "${nonarch_libdir}/systemd/system.conf.d/10-eicke-watchdog.conf"
