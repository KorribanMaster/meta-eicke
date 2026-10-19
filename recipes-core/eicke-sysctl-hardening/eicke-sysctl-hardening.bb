SUMMARY = "Hardening sysctls for eicke production images"
DESCRIPTION = "Ships /etc/sysctl.d/99-eicke-hardening.conf (applied by \
systemd-sysctl at boot): kernel information-leak restrictions, scoped ptrace \
(Yama), unprivileged-BPF off, and network anti-spoofing basics. Installed \
only by the prod images (eicke-prod-hardening.inc); dev images keep the \
kernel defaults so debugging (gdb attach, perf, bpftrace) stays frictionless."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://99-eicke-hardening.conf"
# file://-only recipe: sources land in UNPACKDIR, there is no ${BP} dir.
S = "${UNPACKDIR}"

# systemd-sysctl applies /etc/sysctl.d at boot; the file is baked into the
# image (read-only-rootfs safe -- /etc is overlay-backed on prod anyway).
RDEPENDS:${PN} = "systemd"

do_install() {
    install -d ${D}${sysconfdir}/sysctl.d
    install -m 0644 ${UNPACKDIR}/99-eicke-hardening.conf ${D}${sysconfdir}/sysctl.d/99-eicke-hardening.conf
}

FILES:${PN} = "${sysconfdir}/sysctl.d/99-eicke-hardening.conf"
