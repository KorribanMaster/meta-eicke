SUMMARY = "Initial U-Boot environment (uboot.env) seeded onto the boot partition (A/B state)"
DESCRIPTION = "swupdate's U-Boot bootloader handler read-modify-writes the \
environment and will not create it; this seeds a valid env image so the first \
update can flip the active slot. U-Boot imports the environment from uboot.env \
wholesale (replacing its built-in defaults), so the seed must contain the FULL \
default environment of the u-boot build plus the eicke A/B state \
(rootdev=rootfs_a, ustate=0, bootcount=0). It is built with mkenvimage from \
the u-boot-initial-env text that u-boot deploys. On machines where no U-Boot \
runs (qemuimx8mp-evk) the env only carries the A/B state for swupdate and \
eicke-bootconfirm."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "qemuarm-uboot|qemuimx8mp-evk"
PACKAGE_ARCH = "${MACHINE_ARCH}"

DEPENDS = "u-boot-mkenvimage-native"

# The default environment U-Boot imports along with the A/B state. Empty on
# qemuimx8mp-evk: QEMU boots the kernel directly there, so nothing but
# fw_printenv/fw_setenv (and swupdate) ever reads the env.
EICKE_UBOOTENV_BASE ?= "${DEPLOY_DIR_IMAGE}/u-boot-initial-env"
EICKE_UBOOTENV_BASE:qemuimx8mp-evk = ""
do_compile[depends] += "${@'u-boot:do_deploy' if d.getVar('EICKE_UBOOTENV_BASE') else ''}"

inherit deploy

# Must match CONFIG_ENV_SIZE in the u-boot eicke-env-fat.cfg fragment and the
# env size column in libubootenv's /etc/fw_env.config.
EICKE_UBOOT_ENV_SIZE = "0x10000"

do_compile() {
    : > ${B}/env.txt
    if [ -n "${EICKE_UBOOTENV_BASE}" ]; then
        cat ${EICKE_UBOOTENV_BASE} > ${B}/env.txt
    fi
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
