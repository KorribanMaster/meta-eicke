SUMMARY = "Eicke production initramfs /init (TPM-unlock LUKS /data + /etc overlay)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://eicke-initramfs-init"

do_install() {
    install -m 0755 ${UNPACKDIR}/eicke-initramfs-init ${D}/init
}

FILES:${PN} = "/init"

# This is the PID-1 init of the initramfs; it must not be split/stripped away.
INHIBIT_PACKAGE_STRIP = "1"
RDEPENDS:${PN} = "busybox"
