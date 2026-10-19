SUMMARY = "Initial GRUB environment block seeded onto the ESP (A/B state)"
DESCRIPTION = "swupdate's GRUB bootloader handler read-modify-writes grubenv and \
will not create it; this seeds a valid 1024-byte block (rootdev=rootfs_a, \
ustate=0, bootcount=0) so the first update can flip the active slot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit deploy allarch

# GRUB (and swupdate's grubenv_open) only accept a block of exactly 1024
# bytes: the variables, then '#' padding, no trailing newline. Generated here
# rather than kept as a file in git, where whitespace tooling (an
# end-of-file newline) silently made it 1025 bytes and broke every update.
GRUBENV_SIZE = "1024"

do_compile() {
    printf '# GRUB Environment Block\nrootdev=rootfs_a\nustate=0\nbootcount=0\n' > ${B}/grubenv
    pad=$(expr ${GRUBENV_SIZE} - $(stat -c %s ${B}/grubenv))
    head -c $pad /dev/zero | tr '\0' '#' >> ${B}/grubenv
    [ $(stat -c %s ${B}/grubenv) = ${GRUBENV_SIZE} ] || bbfatal "grubenv is not ${GRUBENV_SIZE} bytes"
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/grubenv ${DEPLOYDIR}/grubenv
}
addtask deploy before do_build after do_install
