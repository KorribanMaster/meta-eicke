SUMMARY = "Eicke production image: hardened (passwords, SSH key auth, read-only rootfs, signed OTA)"
DESCRIPTION = "Production variant of eicke-image: real root+user passwords, SSH \
key authentication with no root password login, only required kernel modules, a \
read-only rootfs with a persistent /etc overlay on the data partition, and \
signature-verified SWUpdate."
LICENSE = "MIT"

require recipes-core/images/eicke-image.bb
# Real password hashes (gitignored; copy from eicke-image-prod-creds.inc.sample).
require recipes-core/images/eicke-image-prod-creds.inc

inherit extrausers overlayfs-etc

# Minimal kernel modules: drop the catch-all (the base installs all modules for
# bring-up) and keep only what this product needs — Realtek NIC (OTA), Intel GPU
# (HDMI console), and the product remoteproc driver (kept from the base). AHCI /
# ext4 / e1000e are built into the kernel (=y), so root-on-SATA needs no module.
IMAGE_INSTALL:remove = "kernel-modules"
IMAGE_INSTALL:append = " kernel-module-r8169 kernel-module-i915"

# Undo the global dev laxness from local.conf's EXTRA_IMAGE_FEATURES so root has
# a real (non-empty) password and sshd isn't auto-loosened to permit root by
# password.
IMAGE_FEATURES:remove = "allow-empty-password empty-root-password allow-root-login"

# ---- SSH: deploy the ed25519 key for root + eicke, harden sshd --------------
# Keys live in root-owned /etc/ssh/authorized_keys.d/<user> (works with a
# read-only rootfs and avoids per-home ownership juggling).
EICKE_SSH_PUBKEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICNIz4EuNBI8LpsfjUk5F+fC0/6Q+STuvnbGuyYBvXm+ eicke@xps-arch"

eicke_prod_ssh_setup() {
    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root \
               ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke

    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d
    cat > ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf <<EOF
# Production SSH policy (eicke-image-prod)
PermitRootLogin prohibit-password
PasswordAuthentication yes
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys /etc/ssh/authorized_keys.d/%u
EOF
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_prod_ssh_setup;"

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
