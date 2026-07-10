SUMMARY = "Eicke production image: hardened (passwords, SSH key auth, read-only rootfs, signed OTA)"
DESCRIPTION = "Production variant of eicke-image: real root+user passwords, SSH \
key authentication with no root password login, only required kernel modules, a \
read-only rootfs with a persistent /etc overlay on the data partition, and \
signature-verified SWUpdate."
LICENSE = "MIT"

# x86 machines get the full hardening incl. UEFI Secure Boot; qemuarm-uboot
# gets everything except the boot-chain/TPM parts (gated :x86-64 below —
# meta-secure-core is UEFI-only, a U-Boot FIT verified-boot chain would be
# the ARM equivalent and is not wired up yet).
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64|qemuarm-uboot"

require recipes-core/images/eicke-image.bb
# Credentials, key-only SSH and dev-laxness removal (shared with netboot-prod).
require recipes-core/images/eicke-prod-hardening.inc

inherit overlayfs-etc

# ---- UEFI Secure Boot (x86 only) ---------------------------------------------
# Production-specific wic whose ESP is populated from the rootfs's /boot/efi
# (signed grub + configs), instead of the bootimg-efi plugin used by base/dev.
# qemuarm-uboot keeps the machine conf's eicke-ab-uboot.wks.in (plain FAT boot
# partition; no signed boot chain on ARM yet).
WKS_FILE:x86-64 = "eicke-ab-prod.wks.in"

# packagegroup-efi-secure-boot pulls the whole signed chain into /boot/efi:
# shim (installed as the firmware default bootx64.efi), SELoader, grub-efi
# (signed grubx64.efi + grub.cfg/boot-menu.inc + .sig + modules), efitools
# (LockDown.efi for key enrollment), efibootmgr and mokutil. It also removes
# the plain (unsigned) grub package.
IMAGE_INSTALL:append:x86-64 = " packagegroup-efi-secure-boot"

# ---- Encrypted /data (LUKS2, TPM2-sealed; x86 only) ---------------------------
# cryptsetup for LUKS, tpm2-tools/cryptfs-tpm2 for sealing the key to PCR7.
# The actual unlock + /etc overlay happens in the initramfs before switch_root
# (replacing the overlayfs-etc preinit); these are also handy on the running
# system for first-boot provisioning of the encrypted /data.
IMAGE_INSTALL:append:x86-64 = " cryptsetup tpm2-tools cryptfs-tpm2"

# Minimal kernel modules: drop the catch-all (the base installs all modules for
# bring-up) and keep only what each machine needs. x86: Realtek NIC (OTA),
# Intel GPU (HDMI console), and the product remoteproc driver (kept from the
# base); AHCI / ext4 / e1000e are built into the kernel (=y). qemuarm-uboot:
# everything it boots with (virtio disk/net) is built into the kernel, so no
# modules are added.
IMAGE_INSTALL:remove = "kernel-modules"
IMAGE_INSTALL:append:x86-64 = " kernel-module-r8169 kernel-module-i915"

# ---- Read-only rootfs + persistent /etc overlay on the data partition --------
# read-only-rootfs makes / immutable (fstab "/" => ro); overlayfs-etc supplies a
# preinit (renamed over /sbin/init) that, before systemd starts, mounts the data
# partition and lays a writable overlay over /etc whose upper dir lives on /data,
# so configuration changes (sshd host keys, machine-id, etc.) persist across
# reboots while the rootfs itself stays read-only. /var stays volatile (tmpfs).
IMAGE_FEATURES:append = " read-only-rootfs overlayfs-etc"

# The preinit runs before udev, so the device must be resolvable by the util-linux
# mount via a blkid scan (no /dev/disk/by-* symlinks yet). The data partition has
# ext4 label "data", so LABEL=data works; a raw node (/dev/sdaN) would not be
# portable across qemu (vdaN) and hardware (sdaN).
OVERLAYFS_ETC_MOUNT_POINT = "/data"
OVERLAYFS_ETC_FSTYPE = "ext4"
OVERLAYFS_ETC_DEVICE = "LABEL=data"
OVERLAYFS_ETC_MOUNT_OPTIONS = "defaults"
OVERLAYFS_ETC_USE_ORIG_INIT_NAME = "1"

# The base image adds a "LABEL=data /data" line to /etc/fstab. With the overlay
# preinit already mounting /data at early boot, that fstab entry would be a second
# mount of the same device on the same point. Drop just the /data line for prod
# (keep the ESP /boot line). Runs after the base's fstab_add_eicke_mounts.
eicke_prod_drop_data_fstab() {
    sed -i '/^LABEL=data[[:space:]]/d' ${IMAGE_ROOTFS}${sysconfdir}/fstab
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_prod_drop_data_fstab;"

# Writable state on the read-only rootfs. systemd services that declare
# StateDirectory= (e.g. systemd-timesyncd -> systemd/timesync,
# systemd-networkd-persistent-storage -> systemd/network) create their dir under
# /var/lib/systemd, which is on the read-only rootfs and otherwise fails with
# "STATE_DIRECTORY ... Read-only file system" (leaving the system "degraded").
# /var/lib/systemd is empty in the image, so backing just it with a tmpfs makes
# those dirs creatable while hiding nothing (other /var/lib entries like dbus
# stay on the rootfs). The state is volatile; persistent config lives in the
# /etc overlay on /data.
eicke_prod_var_state_fstab() {
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/fstab <<EOF
tmpfs                /var/lib/systemd     tmpfs      defaults              0  0
EOF
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_prod_var_state_fstab;"
