# U-Boot half of the eicke A/B scheme (qemuarm-uboot; selected via
# EICKE_BOOTLOADER, see eicke-image.bb).

# libubootenv-bin: fw_printenv/fw_setenv for eicke-bootconfirm (swupdate's
# U-Boot backend links the library). /etc/fw_env.config (-> /boot/uboot.env)
# comes from the libubootenv bbappend.
IMAGE_INSTALL:append = " libubootenv-bin"

# Boot partition payload for the bootimg-partition wic plugin: only the seed
# A/B state environment (rootdev=rootfs_a/ustate=0/bootcount=0), deployed by the
# ubootenv recipe — swupdate's U-Boot handler read-modify-writes it and won't
# create it. The boot *logic* is compiled into U-Boot's default env
# (eicke.env / CONFIG_ENV_SOURCE_FILE, see the u-boot bbappend), so there is no
# boot.scr on FAT.
IMAGE_BOOT_FILES:append = " uboot.env"
do_image_wic[depends] += "ubootenv:do_deploy"
