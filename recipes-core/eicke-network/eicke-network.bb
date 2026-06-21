SUMMARY = "Default DHCP networking (systemd-networkd) for eicke images"
DESCRIPTION = "Ships a DHCP .network for all ethernet interfaces and a systemd \
preset that enables systemd-networkd + systemd-resolved, so eicke images come \
up on the network automatically (useful for headless boards with no console)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://80-dhcp.network \
    file://90-eicke-networkd.preset \
"

# networkd/resolved live in the systemd package; we just configure + enable them.
RDEPENDS:${PN} = "systemd"

do_install() {
    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${UNPACKDIR}/80-dhcp.network ${D}${sysconfdir}/systemd/network/80-dhcp.network

    # Preset is applied at image build (systemctl preset-all) to enable the units.
    install -d ${D}${nonarch_libdir}/systemd/system-preset
    install -m 0644 ${UNPACKDIR}/90-eicke-networkd.preset ${D}${nonarch_libdir}/systemd/system-preset/90-eicke-networkd.preset
}

FILES:${PN} = " \
    ${sysconfdir}/systemd/network/80-dhcp.network \
    ${nonarch_libdir}/systemd/system-preset/90-eicke-networkd.preset \
"
