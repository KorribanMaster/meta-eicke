FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# qemuarm-uboot: store the environment as a FAT file on the boot partition
# (mirrors grubenv-on-ESP, see eicke-env-fat.cfg) and compile the A/B boot
# script. UBOOT_ENV/UBOOT_ENV_SUFFIX make u-boot.inc run `mkimage -T script`
# on boot.cmd and deploy boot.scr (the u-boot-mkimage-native dependency is
# added automatically).
SRC_URI:append:qemuarm-uboot = " \
    file://eicke-env-fat.cfg \
    file://0001-qemu-arm-eicke-env-flags-whitelist.patch \
    file://boot.cmd \
"
UBOOT_ENV:qemuarm-uboot = "boot"
UBOOT_ENV_SUFFIX:qemuarm-uboot = "scr"
