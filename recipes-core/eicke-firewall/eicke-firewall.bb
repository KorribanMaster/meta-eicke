SUMMARY = "Default-deny inbound nftables firewall for eicke production images"
DESCRIPTION = "Loads a static nftables ruleset before the network comes up: \
inbound default-drop with only SSH (22) and the SWUpdate web UI (8080) open. \
The .swu upload stays reachable because signed-bundle verification, not \
network reachability, is the security boundary for updates. Installed only \
by the prod images (eicke-prod-hardening.inc)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://nftables.conf \
    file://eicke-firewall.service \
"
# file://-only recipe: sources land in UNPACKDIR, there is no ${BP} dir.
S = "${UNPACKDIR}"

# nft CLI from meta-networking, plus the nft kernel modules the ruleset uses
# (ct now; limit/log/reject for future rules). NF_TABLES itself is =y; these
# are =m in the kernel-cache nf_tables feature (which overrides user
# fragments, see the linux-yocto bbappend), signed at build (MODULE_SIG_ALL)
# and auto-loaded by nft via request_module. Explicit RDEPENDS because
# eicke-image-prod strips the kernel-modules catch-all; inter-module deps
# (e.g. nf_conntrack for nft_ct) resolve via the generated modules.dep
# RDEPENDS.
RDEPENDS:${PN} = " \
    nftables \
    kernel-module-nft-ct \
    kernel-module-nft-limit \
    kernel-module-nft-log \
    kernel-module-nft-reject-inet \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "eicke-firewall.service"

do_install() {
    # 0600: the ruleset reveals which ports are open/filtered; root-only.
    install -d ${D}${sysconfdir}
    install -m 0600 ${UNPACKDIR}/nftables.conf ${D}${sysconfdir}/nftables.conf

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/eicke-firewall.service ${D}${systemd_system_unitdir}/eicke-firewall.service
}

FILES:${PN} = " \
    ${sysconfdir}/nftables.conf \
    ${systemd_system_unitdir}/eicke-firewall.service \
"
