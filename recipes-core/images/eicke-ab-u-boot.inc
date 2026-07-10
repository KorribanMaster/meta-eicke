# U-Boot half of the eicke A/B scheme (qemuarm-uboot; selected via
# EICKE_BOOTLOADER, see eicke-image.bb).

# libubootenv-bin: fw_printenv/fw_setenv for eicke-bootconfirm (swupdate's
# U-Boot backend links the library). /etc/fw_env.config (-> /boot/uboot.env)
# comes from the libubootenv bbappend.
IMAGE_INSTALL:append = " libubootenv-bin"

# Boot partition payload for the bootimg-partition wic plugin: the compiled
# A/B boot script (deployed by u-boot's UBOOT_ENV machinery, see the u-boot
# bbappend) and the seed environment with rootdev=rootfs_a/ustate=0/
# bootcount=0 (deployed by the ubootenv recipe — swupdate's U-Boot handler
# read-modify-writes the env and won't create it).
IMAGE_BOOT_FILES:append = " boot.scr uboot.env"
do_image_wic[depends] += "u-boot:do_deploy ubootenv:do_deploy"
