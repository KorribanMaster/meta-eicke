SUMMARY = "Zynq RTU remoteproc firmware blob (A9 OpenAMP rpmsg echo)"
DESCRIPTION = "Prebuilt Cortex-A9 firmware ELF that the zynq-pcie-rproc host \
driver loads into the endpoint's DDR over PCIe via request_firmware(). Built \
out-of-band with Xilinx Vitis (firmware/ in the openamp_pcie repo) and vendored \
here as a versioned binary artifact; installed to /lib/firmware/zynq_rtu.elf, \
the basename the driver requests (ZYNQ_RPROC_FW_NAME)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# A target-arch-agnostic data blob for an ARM core — not host (x86) code, so it
# must not be split/stripped or trigger an arch sanity error.
inherit allarch
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} += "arch"

SRC_URI = "file://zynq_rtu.elf"

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware
    install -m 0644 ${UNPACKDIR}/zynq_rtu.elf ${D}${nonarch_base_libdir}/firmware/zynq_rtu.elf
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/zynq_rtu.elf"
