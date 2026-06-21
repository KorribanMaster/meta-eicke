FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Override SWUpdate's build configuration with ours. The recipe is defconfig-
# driven: do_configure cats this onto .config and runs olddefconfig, and it
# scans the defconfig text to compute DEPENDS (e.g. mtd-utils only when
# CONFIG_MTD/CFI=y). Our defconfig disables MTD/CFI and enables the GRUB
# environment bootloader backend + handler.
SRC_URI += "file://defconfig"

# meta-swupdate ships swupdate.service as Type=notify, but we build without
# CONFIG_SYSTEMD (no sd_notify), so systemd times the unit out and the system
# reports "degraded". Drop in a Type=simple override.
SRC_URI += "file://swupdate-type.conf"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}/swupdate.service.d
    install -m 0644 ${UNPACKDIR}/swupdate-type.conf ${D}${systemd_system_unitdir}/swupdate.service.d/10-eicke-type.conf
}

FILES:${PN} += "${systemd_system_unitdir}/swupdate.service.d/10-eicke-type.conf"
