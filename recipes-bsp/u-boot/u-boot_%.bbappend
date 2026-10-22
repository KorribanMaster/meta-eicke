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

# te0820 (Trenz TE0820-02, Zynq UltraScale+): xilinx_zynqmp_virt_defconfig plus
# the board device tree (files/dts, shared with the kernel) and, from the Trenz
# 2024.2 reference design (2cg_1e_2gb prebuilt, see doc/machines/te0820.md for
# why it fits the 1 GiB REV02 module), the PS init (DDR, clocks, MIO) that SPL
# builds in instead of a Xilinx FSBL and the PM configuration object SPL hands
# to the PMU firmware. The PMU firmware boot.bin carries is 2025.2, built from
# the design's XSA (TF-A 2.14 needs it, see the doc). The -u-boot.dtsi keeps
# U-Boot's mmc numbering in hardware order (SPL boots SD1 as mmc 1).
# eicke-te0820.cfg moves the env to uboot.env on the microSD and compiles in
# the A/B bootcmd; the patch whitelists the env variables. TF-A (meta-arm)
# goes into u-boot.itb as BL31.
FILESEXTRAPATHS:prepend:te0820 := "${THISDIR}/../../files/dts:"
SRC_URI:append:te0820 = " \
    file://zynqmp-te0820.dts \
    file://te0820/zynqmp-te0820-u-boot.dtsi \
    file://te0820/psu_init_gpl.c \
    file://te0820/psu_init_gpl.h \
    file://te0820/pmufw.elf \
    file://te0820/pm_cfg_obj.bin \
    file://te0820/elf2bin.py \
    file://te0820/eicke-te0820.cfg \
    file://te0820/0001-zynqmp-eicke-env-flags-whitelist.patch \
    file://te0820/0002-arm64-zynqmp-spl-signal-boot-loader-completion-to-PMU.patch \
"

DEPENDS:append:te0820 = " trusted-firmware-a"
EXTRA_OEMAKE:append:te0820 = " BL31=${RECIPE_SYSROOT}/firmware/trusted-firmware-a/bl31.bin"

# The board's files are not in the U-Boot tree: the DTS goes next to
# zynqmp.dtsi (and into the ZynqMP dtb list), the rest into the directory the
# ZynqMP board Makefile looks up by CONFIG_DEFAULT_DEVICE_TREE. pmufw.elf is
# flattened to the raw image mkimage packs into boot.bin.
do_configure:prepend:te0820() {
    install -m 0644 ${UNPACKDIR}/zynqmp-te0820.dts \
        ${UNPACKDIR}/te0820/zynqmp-te0820-u-boot.dtsi ${S}/arch/arm/dts/
    grep -q 'zynqmp-te0820\.dtb' ${S}/arch/arm/dts/Makefile || \
        sed -i 's/^dtb-$(CONFIG_ARCH_ZYNQMP) += \\$/&\n\tzynqmp-te0820.dtb \\/' ${S}/arch/arm/dts/Makefile
    grep -q 'zynqmp-te0820\.dtb' ${S}/arch/arm/dts/Makefile || bbfatal "zynqmp-te0820.dtb not added to arch/arm/dts/Makefile"
    board=${S}/board/xilinx/zynqmp/zynqmp-te0820
    install -d $board
    install -m 0644 ${UNPACKDIR}/te0820/psu_init_gpl.c ${UNPACKDIR}/te0820/psu_init_gpl.h $board/
    install -m 0644 ${UNPACKDIR}/te0820/pm_cfg_obj.bin $board/
    python3 ${UNPACKDIR}/te0820/elf2bin.py ${UNPACKDIR}/te0820/pmufw.elf $board/pmufw.bin
    # pm_cfg_obj.S .incbin's the object by its path relative to the build
    # directory (the Makefile only resolves it in the source tree for the
    # dependency), so it is needed in both.
    install -D -m 0644 ${UNPACKDIR}/te0820/pm_cfg_obj.bin \
        ${B}/board/xilinx/zynqmp/zynqmp-te0820/pm_cfg_obj.bin
    # Vivado declares its functions with empty parentheses, like ps7_init on
    # ax7015b (here also indented, in the header); the Makefile's
    # CFLAGS_REMOVE_psu_init_gpl.o does not reach the object in the board
    # subdirectory. Fix the copies, not the files in the layer.
    sed -i -E 's/^( ?[A-Za-z_][A-Za-z0-9_ *]*[A-Za-z0-9_]) ?\(\)/\1(void)/' \
        $board/psu_init_gpl.c $board/psu_init_gpl.h
}

# SPL loads U-Boot from a file named after its own build,
# u-boot-<16 hex digits of do_compile's task hash>.itb, instead of the fixed
# u-boot.itb. Each boot.bin is then paired with exactly its own u-boot.itb,
# and a bootloader update (eicke-update-bootloader) writes the new .itb next to
# the current one and switches with the single rename of boot.bin. The task
# hash covers everything that goes into both files (U-Boot, the board files,
# TF-A, the PMU firmware); it is excluded from the hash itself.
do_compile:prepend:te0820() {
    hash=${BB_TASKHASH}
    [ ${#hash} -ge 16 ] || bbfatal "no task hash to name u-boot.itb after"
    itb=u-boot-$(echo $hash | cut -c1-16).itb
    sed -i "s/^CONFIG_SPL_FS_LOAD_PAYLOAD_NAME=.*/CONFIG_SPL_FS_LOAD_PAYLOAD_NAME=\"$itb\"/" ${B}/.config
    grep -qx "CONFIG_SPL_FS_LOAD_PAYLOAD_NAME=\"$itb\"" ${B}/.config || \
        bbfatal "CONFIG_SPL_FS_LOAD_PAYLOAD_NAME not in ${B}/.config"
}

# binman names the FIT after CONFIG_SPL_FS_LOAD_PAYLOAD_NAME; oe-core's
# install and deploy expect u-boot.itb.
do_compile:append:te0820() {
    itb=$(sed -n 's/^CONFIG_SPL_FS_LOAD_PAYLOAD_NAME="\(.*\)"$/\1/p' ${B}/.config)
    cp ${B}/$itb ${B}/u-boot.itb
}

# Deploy u-boot.itb under the name SPL loads, too (eicke-esp/, so the image's
# IMAGE_BOOT_FILES glob only matches it, not the usual u-boot-<machine>-*.itb).
do_deploy:append:te0820() {
    itb=$(sed -n 's/^CONFIG_SPL_FS_LOAD_PAYLOAD_NAME="\(.*\)"$/\1/p' ${B}/.config)
    [ -n "$itb" ] || bbfatal "CONFIG_SPL_FS_LOAD_PAYLOAD_NAME not in ${B}/.config"
    install -D -m 0644 ${B}/u-boot.itb ${DEPLOYDIR}/eicke-esp/$itb
}
