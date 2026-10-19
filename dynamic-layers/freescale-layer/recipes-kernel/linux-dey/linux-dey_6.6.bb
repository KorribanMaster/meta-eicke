SUMMARY = "Digi Linux kernel (NXP 6.6 based) for the ConnectCore 8M Nano DVK"
DESCRIPTION = "Digi's public kernel fork with the ccimx8mn SOM/DVK device trees \
(digi/ccimx8mn-dvk.dts + overlays) and drivers (MCA) that mainline lacks. \
Ported from meta-digi (scarthgap) linux-dey_6.6.bb without TrustFence/DEY \
extras, built the meta-freescale linux-imx way (in-tree defconfig + kernel-yocto \
fragments) plus the eicke fragments shared with linux-yocto and the eicke CM7 \
overlay for the RustAmp firmware."
HOMEPAGE = "https://github.com/digi-embedded/linux"
SECTION = "kernel"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel-yocto kernel fsl-kernel-localversion

COMPATIBLE_MACHINE = "ccimx8mn-dvk"

# Same tree and revision as meta-digi scarthgap's linux-dey_6.6.bb (6.6.52).
SRCBRANCH = "v6.6/nxp/dey-5.0/maint"
KBRANCH = "${SRCBRANCH}"
SRCREV = "e7c82eab6fe779b075fb57652b929aae8194d263"
LINUX_VERSION = "6.6.52"
PV = "${LINUX_VERSION}+git"
LOCALVERSION = "-dey"
SCMVERSION ?= "y"

# eicke fragments shared with linux-yocto (recipes-kernel/linux/linux-yocto)
# and this machine's own files.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}/../../../../recipes-kernel/linux/linux-yocto:"
SRC_URI = " \
    git://github.com/digi-embedded/linux.git;protocol=https;branch=${SRCBRANCH} \
    file://zynq-rproc.cfg \
    file://overlayfs.cfg \
    file://hardening.cfg \
    file://ccimx8mn-rproc.cfg \
    file://ccimx8mn-no-qoriq.cfg \
    file://eicke-ccimx8mn-cm7.dtso \
"

# In-tree defconfig expanded with alldefconfig, then the fragments merged on top
# (unlike linux-yocto there is no kernel-cache, so the defconfig is the base the
# fragments override).
KBUILD_DEFCONFIG = "ccimx8_defconfig"
KCONFIG_MODE = "--alldefconfig"
KERNEL_EXTRA_ARGS += "LOADADDR=${UBOOT_ENTRYPOINT}"

# The eicke CM7 overlay lives next to Digi's; list it in the digi dts Makefile
# so it is built like the other dtbos (base DTBs there get -@ symbols).
do_configure:append() {
    install -m 0644 ${UNPACKDIR}/eicke-ccimx8mn-cm7.dtso ${S}/arch/arm64/boot/dts/digi/
    grep -q eicke-ccimx8mn-cm7.dtbo ${S}/arch/arm64/boot/dts/digi/Makefile || \
        echo 'dtb-$(CONFIG_ARCH_MXC) += eicke-ccimx8mn-cm7.dtbo' >> ${S}/arch/arm64/boot/dts/digi/Makefile
}
