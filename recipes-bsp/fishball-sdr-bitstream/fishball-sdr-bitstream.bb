SUMMARY = "PL bitstream of the Fish Ball SDR (AD936x interface, ADI fmcomms2 design)"
DESCRIPTION = "The vendor's prebuilt bitstream for the Zynq-7020 PL: ADI's \
axi_ad9361 core, the RX/TX axi_dmac and the pack/unpack FIFOs of the fmcomms2 \
reference design, retargeted to the board. U-Boot loads it from the active \
slot's /boot before the kernel, so it is updated and rolled back with the rootfs."
# A Vivado output from the vendor's package (7020_AD936X_SDR, ZC706-FMCOMMS2-3
# port, system_top.xsa); no license is stated for it.
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "fishball-sdr"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# system_top.bit from the top-level system_top.xsa in
# HDL-Vivado2021.1/7020_SDR_ZC706_My.zip (Vivado 2022.2, 2024-09-24 19:57).
# Its configuration data is byte for byte the bitstream in the vendor's
# PetaLinux BOOT.BIN, and the xsa's ps7_init is the one U-Boot SPL builds in
# (doc/machines/fishball-sdr.md). Kept as a .bit: U-Boot's "fpga loadb" reads
# the header.
SRC_URI = "file://fishball-sdr.bit"

S = "${UNPACKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -D -m 0644 ${S}/fishball-sdr.bit ${D}/boot/fishball-sdr.bit
}

FILES:${PN} = "/boot/fishball-sdr.bit"
