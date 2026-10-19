SUMMARY = "Digi U-Boot (v2024.04/maint) for the ConnectCore 8M Nano DVK, with the eicke A/B boot logic"
DESCRIPTION = "Digi's public U-Boot fork, which carries the ccimx8mn SOM/DVK \
support (LPDDR4 timing, MCA, HWID) that mainline U-Boot lacks. Ported from \
meta-digi (scarthgap) without TrustFence/DEY extras. eicke adds the A/B state in \
uboot.env on the microSD's FAT boot partition, an env variable whitelist and \
the slot logic as the compiled-in bootcmd (same contract as qemuarm-uboot)."
HOMEPAGE = "https://github.com/digi-embedded/u-boot"
SECTION = "bootloaders"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=2ca5f2c35c8cc335f0a19756634782f1"

require recipes-bsp/u-boot/u-boot.inc

PROVIDES += "u-boot"
COMPATIBLE_MACHINE = "ccimx8mn-dvk"

DEPENDS += "bc-native bison-native dtc-native flex-native gnutls-native xxd-native"

# Same tree and revision as meta-digi scarthgap's u-boot-dey_2024.04.bb.
SRCBRANCH = "v2024.04/maint"
SRCREV = "f826b7bf47f7d4cc3bf3849967e58d343ad542e6"
SRC_URI = " \
    git://github.com/digi-embedded/u-boot.git;protocol=https;branch=${SRCBRANCH} \
    file://eicke-env-fat-ccimx8mn.cfg \
    file://eicke-bootcmd-ccimx8mn.cfg \
    file://0001-ccimx8m-whitelist-the-eicke-A-B-env-variables.patch \
"

B = "${WORKDIR}/build"

# ubootenv.bb seeds uboot.env from the deployed default environment under this
# name (the class default would be ${PN}-initial-env).
UBOOT_INITIAL_ENV = "u-boot-initial-env"

# The boot container is built by imx-boot (imx-mkimage) from the deployed SPL,
# u-boot-nodtb.bin and the U-Boot DTB, as for meta-freescale's u-boot-imx.
BOOT_TOOLS = "imx-boot-tools"

# U-Boot 2024.04 predates GCC 15, whose C23 default breaks the host tools
# (bool/true/false keywords); the target build already uses -std=gnu11.
EXTRA_OEMAKE:append = ' HOSTCFLAGS="${BUILD_CFLAGS} -std=gnu11"'

# Deploy u-boot-nodtb.bin and the U-Boot DTB for imx-boot (trimmed from
# meta-freescale's u-boot-imx do_deploy:append:mx8m-generic-bsp).
do_deploy:append:mx8m-generic-bsp() {
    i=0
    for config in ${UBOOT_MACHINE}; do
        i=$(expr $i + 1)
        j=0
        for type in ${UBOOT_CONFIG}; do
            j=$(expr $j + 1)
            [ $j -eq $i ] || continue
            builddir="${config}-${type}"
            install -d ${DEPLOYDIR}/${BOOT_TOOLS}
            install -m 0644 ${B}/${builddir}/u-boot-nodtb.bin \
                ${DEPLOYDIR}/${BOOT_TOOLS}/u-boot-nodtb.bin-${MACHINE}-${type}
            install -m 0644 ${B}/${builddir}/arch/arm/dts/${UBOOT_DTB_NAME} \
                ${DEPLOYDIR}/${BOOT_TOOLS}/${UBOOT_DTB_NAME}
            install -m 0644 ${B}/${builddir}/arch/arm/dts/${UBOOT_DTB_NAME} \
                ${DEPLOYDIR}/${BOOT_TOOLS}/${UBOOT_DTB_NAME}-${type}
        done
    done
}
