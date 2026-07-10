# U-Boot A/B boot script for meta-eicke (qemuarm-uboot) — the U-Boot mirror of
# files/wic/grub.cfg.
#
# `rootdev` (rootfs_a | rootfs_b) is persisted in uboot.env on the FAT boot
# partition (CONFIG_ENV_IS_IN_FAT; already loaded into the environment before
# this script runs) and flipped by SWUpdate's U-Boot bootloader handler after
# writing the standby slot. A bootcount/ustate rollback switches back to the
# previous slot if a freshly-updated image fails to confirm a healthy boot.
# saveenv writes the whole environment back to the same uboot.env file.
#
# Per-slot kernel: the kernel is loaded from the *active slot's* /boot, so a
# rootfs update (which carries /boot/zImage) updates the kernel too. We address
# the slot by GPT partition number on the boot disk (the raw image write
# clobbers the ext4 label); the kernel then mounts root via PARTLABEL (GPT
# name, untouched by the write).

# First boot (no eicke vars in env yet) -> slot A
if test -z "${rootdev}"; then
    setenv rootdev rootfs_a
fi

# Rollback: ustate=1 means "update on trial". Give it one boot; if it comes
# back unconfirmed (eicke-bootconfirm didn't clear ustate), switch to the
# other slot and mark FAILED (ustate=3).
if test "${ustate}" = "1"; then
    if test "${bootcount}" = "1"; then
        if test "${rootdev}" = "rootfs_a"; then
            setenv rootdev rootfs_b
        else
            setenv rootdev rootfs_a
        fi
        setenv ustate 3
        saveenv
    else
        setenv bootcount 1
        saveenv
    fi
fi

# Map the active slot to its GPT partition number (matches the .wks order:
# p1=esp, p2=rootfs_a, p3=rootfs_b, p4=data).
if test "${rootdev}" = "rootfs_a"; then
    setenv slot 2
else
    setenv slot 3
fi

# console=ttyAMA0,115200: qemu virt's PL011 serial console.
setenv bootargs "root=PARTLABEL=${rootdev} rootwait console=ttyAMA0,115200"

echo "Eicke Linux (${rootdev}): loading /boot/zImage from virtio 0:${slot}"
ext4load virtio 0:${slot} ${kernel_addr_r} /boot/zImage

# Boot with the DTB QEMU generated and handed to U-Boot (fdtcontroladdr) — the
# guest hardware is defined by QEMU itself, no on-disk DTB needed. The blob
# sits inside U-Boot's reserved memory where bootz's FDT reservation fails
# ("Failed to reserve memory for fdt"), so copy it into free RAM first (the
# unused ramdisk_addr_r slot; a generous 1 MiB covers any qemu-virt DTB).
setenv fdt_addr_r 0x44000000
cp.b ${fdtcontroladdr} ${fdt_addr_r} 0x100000
bootz ${kernel_addr_r} - ${fdt_addr_r}
