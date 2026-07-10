# GRUB-EFI half of the eicke A/B scheme (x86/EFI machines; selected via
# EICKE_BOOTLOADER, see eicke-image.bb).

# grub-editenv: swupdate's GRUB handler and eicke-bootconfirm read/write the
# boot environment (grubenv) with it.
IMAGE_INSTALL:append = " grub-editenv"

# The ESP holds GRUB + grub.cfg + grubenv and is mounted at /boot so SWUpdate
# (and grub-editenv) can read/write the boot environment. Seed an initial
# grubenv onto the ESP at EFI/BOOT/grubenv (swupdate's GRUB handler won't
# create it) via the grubenv recipe + IMAGE_BOOT_FILES.
# NOTE: the bootimg-efi wic plugin reads IMAGE_EFI_BOOT_FILES (not the
# bootimg-partition IMAGE_BOOT_FILES) for files placed on the ESP.
# Under efi-secure-boot the grub-efi recipe creates+ships grubenv on the ESP
# itself (and would conflict with this standalone seed), so only use the
# grubenv recipe when secure boot is off (the bootimg-efi flow).
IMAGE_EFI_BOOT_FILES:append = "${@bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', '', ' grubenv;EFI/BOOT/grubenv', d)}"
do_image_wic[depends] += "${@bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', '', 'grubenv:do_deploy', d)}"

# grub.cfg is pulled in at wic runtime via the wks 'bootloader --configfile',
# so bitbake doesn't track it automatically; register it as a task input so
# edits to the A/B boot config actually trigger a wic rebuild.
do_image_wic[file-checksums] += "${THISDIR}/../../files/wic/grub.cfg:True"
