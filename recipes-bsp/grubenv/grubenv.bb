SUMMARY = "Initial GRUB environment block seeded onto the ESP (A/B state)"
DESCRIPTION = "swupdate's GRUB bootloader handler read-modify-writes grubenv and \
will not create it; this seeds a valid 1024-byte block (rootdev=rootfs_a, \
ustate=0, bootcount=0) so the first update can flip the active slot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://grubenv"

inherit deploy allarch

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${WORKDIR}/grubenv ${DEPLOYDIR}/grubenv
}
addtask deploy before do_build after do_install
