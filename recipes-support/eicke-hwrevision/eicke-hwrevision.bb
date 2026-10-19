SUMMARY = "SWUpdate /etc/hwrevision: board identity for hardware-compatibility gating"
DESCRIPTION = "Writes /etc/hwrevision ('boardname revision'). With swupdate's \
CONFIG_HW_COMPATIBILITY=y (our defconfig) a .swu is refused unless its \
sw-description hardware-compatibility list contains this revision string, so \
a bundle built for another machine can no longer be flashed. The bitbake \
MACHINE is used as the revision, matching the @@MACHINE@@ the swupdate class \
expands into every sw-description."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Board name is constant; the revision carries the machine. Overridable for
# real hardware revisions later (e.g. EICKE_HW_REVISION = "genericx86-64-rev2"
# in a board conf -- then extend hardware-compatibility in the
# sw-descriptions to list every revision the bundle supports).
EICKE_HW_BOARD ?= "eicke"
EICKE_HW_REVISION ?= "${MACHINE}"

# The file content depends on MACHINE, not just the tune.
PACKAGE_ARCH = "${MACHINE_ARCH}"

do_install() {
    install -d ${D}${sysconfdir}
    echo "${EICKE_HW_BOARD} ${EICKE_HW_REVISION}" > ${D}${sysconfdir}/hwrevision
    chmod 0644 ${D}${sysconfdir}/hwrevision
}

FILES:${PN} = "${sysconfdir}/hwrevision"
