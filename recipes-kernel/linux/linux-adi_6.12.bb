SUMMARY = "Analog Devices Linux kernel (AD936x, AXI DMAC/ADC/DDS cores) for the Fish Ball SDR"
DESCRIPTION = "ADI's kernel fork, release 2026_R1 (6.12). Mainline has no \
driver for the AD9361/AD9363/AD9364 or ADI's cf_axi_adc/cf_axi_dds converter \
cores, which the SDR's PL design and the IIO tools (iiod, IIO Oscilloscope, \
SDR#, GNU Radio, pyadi-iio) need. Configured from ADI's Zynq defconfig plus \
the eicke fragments shared with linux-yocto (hardening, overlayfs) and the \
board's fragment."

# linux-yocto.inc: kernel + kernel-yocto, so the .cfg fragments in SRC_URI are
# merged into the configuration exactly as for linux-yocto. The tree is not a
# Yocto kernel; it is configured from the in-tree defconfig (KBUILD_DEFCONFIG)
# instead of a kernel-cache BSP.
require recipes-kernel/linux/linux-yocto.inc
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

LINUX_VERSION = "6.12"
LINUX_VERSION_EXTENSION = "-adi"
PV = "${LINUX_VERSION}+git"

# 2026_R1 (6.12.0).
SRCREV_machine = "40201abd70d8f7848547a09fc7e366fba064483c"
# kernel-cache's metadata only provides the cfg/*.scc features oe-core adds by
# MACHINE_FEATURES (linux-yocto.inc, e.g. cfg/fs/vfat.scc); there is no BSP
# description for this machine in it.
SRCREV_meta = "4b64f290ab9ca1947c3848e049768421f74bf263"
SRCREV_FORMAT = "machine_meta"

# The full ADI history is several GB; a shallow clone of the release is
# enough.
BB_GIT_SHALLOW = "1"

SRC_URI = " \
    git://github.com/analogdevicesinc/linux.git;protocol=https;branch=2026_R1;name=machine \
    git://git.yoctoproject.org/yocto-kernel-cache;type=kmeta;name=meta;branch=yocto-6.12;destsuffix=${KMETA};protocol=https \
"
KMETA = "kernel-meta"

# ADI's configuration for its Zynq-7000 evaluation boards (ZC702/ZC706/ZED with
# FMCOMMS2/3): the Zynq PS drivers, ext4, and all ADI drivers built in.
KBUILD_DEFCONFIG = "zynq_xcomm_adv7511_defconfig"
KCONFIG_MODE = "alldefconfig"

# The eicke fragments every kernel of the layer gets (linux-yocto bbappend:
# hardening, overlayfs; zynq-rproc.cfg is left out, there is no AMP here),
# and the board's.
FILESEXTRAPATHS:prepend := "${THISDIR}/linux-yocto:${THISDIR}/../../files/dts:"
SRC_URI += " \
    file://hardening.cfg \
    file://overlayfs.cfg \
    file://fishball-sdr.cfg \
    file://zynq-fishball-sdr.dts \
    file://zynq-fishball-sdr-ad936x.dts \
"

COMPATIBLE_MACHINE = "fishball-sdr"
KMACHINE = "fishball-sdr"

# The board device trees (files/dts, the PS part shared with U-Boot) go next
# to ADI's zynq-7000.dtsi and adi-fmcomms2.dtsi; kbuild builds xilinx/<name>.dtb
# without a Makefile entry.
do_configure:append() {
    install -m 0644 ${UNPACKDIR}/zynq-fishball-sdr.dts \
        ${UNPACKDIR}/zynq-fishball-sdr-ad936x.dts ${S}/arch/arm/boot/dts/xilinx/
}

# Fail the build if a =y of the board fragment did not make it into .config
# (a symbol whose dependency is off is dropped silently).
EICKE_CHECK_FRAGMENTS = "fishball-sdr.cfg"
require recipes-kernel/linux/eicke-check-fragment.inc
