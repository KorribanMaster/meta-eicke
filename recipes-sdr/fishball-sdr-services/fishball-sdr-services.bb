SUMMARY = "Pluto-style services for the Fish Ball SDR"
DESCRIPTION = "What makes the board behave like an ADALM-Pluto, ported from \
ADI's pluto-fw (board/pluto S23udc and S21misc) to systemd: the USB gadget \
(RNDIS/ECM/NCM network at 192.168.2.1, ACM console, iiod over FunctionFS), a \
permanent MAC address, and the AD936x runtime settings from uboot.env."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "fishball-sdr"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = " \
    file://fishball-sdr-gadget \
    file://fishball-sdr-gadget.service \
    file://fishball-sdr-gadget-bind.service \
    file://iiod \
    file://70-usb0.network \
    file://fishball-sdr-mac \
    file://fishball-sdr-mac.service \
    file://ad936x-config \
    file://ad936x-config.service \
    file://serial-getty-ttyGS0.conf \
"

S = "${UNPACKDIR}"

inherit systemd

PACKAGES = "fishball-sdr-gadget fishball-sdr-mac ad936x-config"

SYSTEMD_PACKAGES = "${PACKAGES}"
SYSTEMD_SERVICE:fishball-sdr-gadget = "fishball-sdr-gadget.service fishball-sdr-gadget-bind.service"
SYSTEMD_SERVICE:fishball-sdr-mac = "fishball-sdr-mac.service"
SYSTEMD_SERVICE:ad936x-config = "ad936x-config.service"

do_install() {
    install -d ${D}${libexecdir} ${D}${systemd_system_unitdir}
    for f in fishball-sdr-gadget fishball-sdr-mac ad936x-config; do
        install -m 0755 ${S}/$f ${D}${libexecdir}/$f
    done
    for f in fishball-sdr-gadget.service fishball-sdr-gadget-bind.service \
             fishball-sdr-mac.service ad936x-config.service; do
        install -m 0644 ${S}/$f ${D}${systemd_system_unitdir}/$f
    done

    install -D -m 0644 ${S}/iiod ${D}${sysconfdir}/default/iiod
    install -D -m 0644 ${S}/70-usb0.network ${D}${sysconfdir}/systemd/network/70-usb0.network

    # A login on the gadget's ACM console (ttyGS0 exists once the gadget's
    # acm function is created), from systemd's serial-getty@ template.
    install -d ${D}${sysconfdir}/systemd/system/getty.target.wants
    ln -s ${systemd_system_unitdir}/serial-getty@.service \
        ${D}${sysconfdir}/systemd/system/getty.target.wants/serial-getty@ttyGS0.service
    install -D -m 0644 ${S}/serial-getty-ttyGS0.conf \
        ${D}${systemd_system_unitdir}/serial-getty@ttyGS0.service.d/10-fishball-sdr.conf
}

FILES:fishball-sdr-gadget = " \
    ${libexecdir}/fishball-sdr-gadget \
    ${systemd_system_unitdir}/fishball-sdr-gadget.service \
    ${systemd_system_unitdir}/fishball-sdr-gadget-bind.service \
    ${sysconfdir}/default/iiod \
    ${sysconfdir}/systemd/network/70-usb0.network \
    ${sysconfdir}/systemd/system/getty.target.wants/serial-getty@ttyGS0.service \
    ${systemd_system_unitdir}/serial-getty@ttyGS0.service.d \
"
FILES:fishball-sdr-mac = " \
    ${libexecdir}/fishball-sdr-mac \
    ${systemd_system_unitdir}/fishball-sdr-mac.service \
"
FILES:ad936x-config = " \
    ${libexecdir}/ad936x-config \
    ${systemd_system_unitdir}/ad936x-config.service \
"

# fw_printenv/fw_setenv (uboot.env), and the IIO daemon the gadget's
# FunctionFS function feeds.
RDEPENDS:fishball-sdr-gadget = "libubootenv-bin libiio-iiod"
RDEPENDS:fishball-sdr-mac = "libubootenv-bin"
RDEPENDS:ad936x-config = "libubootenv-bin"
