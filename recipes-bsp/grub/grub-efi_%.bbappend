# Embed the 'regexp' module in the GRUB EFI image so the A/B grub.cfg can
# derive the boot drive from ${root} and address the active slot's partition
# (to load that slot's own kernel). The upstream default GRUB_BUILDIN omits it.
GRUB_BUILDIN:append = " regexp"

# Under meta-efi-secure-boot, grub.cfg sources a signed boot-menu.inc that holds
# the actual menuentry. Shadow the layer's sample boot-menu.inc with our A/B
# version so our slot-selection + kernel cmdline is what gets signed/verified.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
