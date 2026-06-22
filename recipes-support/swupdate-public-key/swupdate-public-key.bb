SUMMARY = "SWUpdate signature-verification public key + config"
DESCRIPTION = "Installs the RSA public key SWUpdate uses to verify the RSA-PSS \
signature on every .swu bundle, plus /etc/swupdate.cfg pointing verification at \
it. The public key is not secret; the matching private key is kept outside the \
tree. With CONFIG_SIGNED_IMAGES every .swu must carry a valid signature."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://swupdate-public.pem \
           file://swupdate.cfg"

# Config + key only; no compilation, and they are the same for every machine.
inherit allarch

do_install() {
    install -d ${D}${sysconfdir}/swupdate
    install -m 0644 ${UNPACKDIR}/swupdate-public.pem ${D}${sysconfdir}/swupdate/public.pem
    install -m 0644 ${UNPACKDIR}/swupdate.cfg ${D}${sysconfdir}/swupdate.cfg
}

FILES:${PN} = "${sysconfdir}/swupdate/public.pem ${sysconfdir}/swupdate.cfg"
