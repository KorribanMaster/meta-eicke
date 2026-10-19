FILESEXTRAPATHS:prepend := "${THISDIR}/libubootenv:"

# eicke-ubootenv machines (qemuarm-uboot, qemuimx8mp-evk): point
# fw_printenv/fw_setenv (and swupdate's U-Boot backend) at the env file on the
# FAT boot partition mounted at /boot. libubootenv does not ship a
# fw_env.config of its own.
SRC_URI:append:eicke-ubootenv = " file://fw_env.config"

do_install:append:eicke-ubootenv() {
    install -Dm 0644 ${UNPACKDIR}/fw_env.config ${D}${sysconfdir}/fw_env.config
}
FILES:${PN}-bin:append:eicke-ubootenv = " ${sysconfdir}/fw_env.config"

# The added config is machine-specific in an otherwise tune-arch package.
PACKAGE_ARCH:eicke-ubootenv = "${MACHINE_ARCH}"
