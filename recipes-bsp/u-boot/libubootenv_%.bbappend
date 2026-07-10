FILESEXTRAPATHS:prepend := "${THISDIR}/libubootenv:"

# qemuarm-uboot: point fw_printenv/fw_setenv (and swupdate's U-Boot backend)
# at the env file on the FAT boot partition mounted at /boot. libubootenv
# does not ship a fw_env.config of its own.
SRC_URI:append:qemuarm-uboot = " file://fw_env.config"

do_install:append:qemuarm-uboot() {
    install -Dm 0644 ${UNPACKDIR}/fw_env.config ${D}${sysconfdir}/fw_env.config
}
FILES:${PN}-bin:append:qemuarm-uboot = " ${sysconfdir}/fw_env.config"

# The added config is machine-specific in an otherwise tune-arch package.
PACKAGE_ARCH:qemuarm-uboot = "${MACHINE_ARCH}"
