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
