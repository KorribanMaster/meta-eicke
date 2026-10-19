FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# qemuarm-uboot: store the A/B *state* (rootdev/ustate/bootcount) as a FAT file
# on the boot partition (eicke-env-fat.cfg + the whitelist patch), and compile
# the A/B boot *logic* into U-Boot's default environment as the built-in bootcmd
# (eicke-bootcmd.cfg). The logic is thus part of the signable U-Boot binary, not
# a tamperable boot.scr on FAT — the ARM analog of x86's signed grub
# boot-menu.inc.
SRC_URI:append:qemuarm-uboot = " \
    file://eicke-env-fat.cfg \
    file://eicke-bootcmd.cfg \
    file://0001-qemu-arm-eicke-env-flags-whitelist.patch \
"

# ax7015b (ALINX AX7015B, Zynq-7000): xilinx_zynq_virt_defconfig plus the board
# device tree (files/dts, shared with the kernel) and the PS init (DDR, clocks,
# MIO) from the Vivado reference design, which SPL builds in instead of a
# Xilinx FSBL. eicke-ax7015b.cfg moves the env to uboot.env on the microSD and
# compiles in the A/B bootcmd; the patch whitelists the env variables.
FILESEXTRAPATHS:prepend:ax7015b := "${THISDIR}/../../files/dts:"
SRC_URI:append:ax7015b = " \
    file://zynq-ax7015b.dts \
    file://ax7015b/ps7_init_gpl.c \
    file://ax7015b/ps7_init_gpl.h \
    file://ax7015b/eicke-ax7015b.cfg \
    file://ax7015b/0001-zynq-eicke-env-flags-whitelist.patch \
"

# The board's files are not in the U-Boot tree: the DTS goes next to
# zynq-7000.dtsi (and into the Zynq dtb list), ps7_init_gpl.c/h into the
# directory the Zynq board Makefile looks up by CONFIG_DEFAULT_DEVICE_TREE.
do_configure:prepend:ax7015b() {
    install -m 0644 ${UNPACKDIR}/zynq-ax7015b.dts ${S}/arch/arm/dts/
    grep -q 'zynq-ax7015b\.dtb' ${S}/arch/arm/dts/Makefile || \
        sed -i 's/^dtb-$(CONFIG_ARCH_ZYNQ) += \\$/&\n\tzynq-ax7015b.dtb \\/' ${S}/arch/arm/dts/Makefile
    grep -q 'zynq-ax7015b\.dtb' ${S}/arch/arm/dts/Makefile || bbfatal "zynq-ax7015b.dtb not added to arch/arm/dts/Makefile"
    install -d ${S}/board/xilinx/zynq/zynq-ax7015b
    install -m 0644 ${UNPACKDIR}/ax7015b/ps7_init_gpl.c ${UNPACKDIR}/ax7015b/ps7_init_gpl.h \
        ${S}/board/xilinx/zynq/zynq-ax7015b/
    # Vivado declares its functions with empty parentheses ("int ps7_init();"),
    # which U-Boot's -Werror=strict-prototypes rejects; the in-tree boards carry
    # copies fixed to (void). Fix the column-0 declarations/definitions here, so
    # the files in the layer stay exactly as Vivado exports them.
    sed -i -E 's/^([A-Za-z_][A-Za-z0-9_ *]*[A-Za-z0-9_]) ?\(\)/\1(void)/' \
        ${S}/board/xilinx/zynq/zynq-ax7015b/ps7_init_gpl.c \
        ${S}/board/xilinx/zynq/zynq-ax7015b/ps7_init_gpl.h
}
