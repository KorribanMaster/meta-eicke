FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Override SWUpdate's build configuration with ours. The recipe is defconfig-
# driven: do_configure cats this onto .config and runs olddefconfig, and it
# scans the defconfig text to compute DEPENDS (e.g. mtd-utils only when
# CONFIG_MTD/CFI=y). Our defconfig disables MTD/CFI and enables the GRUB
# environment bootloader backend + handler.
SRC_URI += "file://defconfig"
