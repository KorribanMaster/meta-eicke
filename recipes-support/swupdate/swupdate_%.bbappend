FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Use our SWUpdate build configuration (GRUB bootloader handler + ext4/raw
# handler + local web/IPC update, no suricatta/hawkBit).
SRC_URI += "file://defconfig"

# Pull in the GRUB environment bootloader handler at build time.
PACKAGECONFIG:append = " bootloader-grub"
