SUMMARY = "Initial U-Boot environment (uboot.env) seeded onto the boot partition (A/B state)"
DESCRIPTION = "swupdate's U-Boot bootloader handler read-modify-writes the \
environment and will not create it; this seeds a valid env image so the first \
update can flip the active slot. U-Boot imports the environment from uboot.env \
wholesale (replacing its built-in defaults), so the seed must contain the FULL \
default environment of the u-boot build plus the eicke A/B state \
(rootdev=rootfs_a, ustate=0, bootcount=0). It is built with mkenvimage from \
the u-boot-initial-env text that u-boot deploys."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "qemuarm-uboot"
PACKAGE_ARCH = "${MACHINE_ARCH}"

DEPENDS = "u-boot-mkenvimage-native"
do_compile[depends] += "u-boot:do_deploy"

inherit deploy

# Must match CONFIG_ENV_SIZE in the u-boot eicke-env-fat.cfg fragment and the
# env size column in libubootenv's /etc/fw_env.config.
EICKE_UBOOT_ENV_SIZE = "0x10000"

do_compile() {
    cat ${DEPLOY_DIR_IMAGE}/u-boot-initial-env > ${B}/env.txt
    cat >> ${B}/env.txt <<EOF
rootdev=rootfs_a
ustate=0
bootcount=0
EOF
    uboot-mkenvimage -s ${EICKE_UBOOT_ENV_SIZE} -o ${B}/uboot.env ${B}/env.txt
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/uboot.env ${DEPLOYDIR}/uboot.env
}
addtask deploy after do_compile before do_build
