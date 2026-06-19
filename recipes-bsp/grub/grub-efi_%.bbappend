# Embed the 'regexp' module in the GRUB EFI image so the A/B grub.cfg can
# derive the boot drive from ${root} and address the active slot's partition
# (to load that slot's own kernel). The upstream default GRUB_BUILDIN omits it.
GRUB_BUILDIN:append = " regexp"
