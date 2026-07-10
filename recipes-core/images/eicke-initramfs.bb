SUMMARY = "Eicke production initramfs: TPM-unlock LUKS /data, /etc overlay, switch_root"
LICENSE = "MIT"

# prod (TPM/LUKS) initramfs; x86 machines only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

# Minimal early-userspace. dm-crypt, overlayfs, ext4 and the TPM TIS/CRB drivers
# are built into the kernel (=y), so no kernel modules are needed here.
PACKAGE_INSTALL = " \
    eicke-initramfs-init \
    busybox \
    util-linux-blkid \
    util-linux-findfs \
    cryptsetup \
    systemd-crypt \
    e2fsprogs-mke2fs \
    libtss2-tcti-device \
    base-files \
    base-passwd \
    ${VIRTUAL-RUNTIME_base-utils} \
"
# systemd-crypt brings systemd-cryptenroll + the libcryptsetup systemd-tpm2 token
# plugin (under ${libdir}/cryptsetup) used by `cryptsetup open --token-only`.

# A custom /init (from eicke-initramfs-init) is PID 1 — don't pull the
# initramfs-framework's /init.
INITRAMFS_SCRIPTS = ""
IMAGE_FEATURES = ""
IMAGE_LINGUAS = ""

export IMAGE_BASENAME = "eicke-initramfs"
IMAGE_NAME_SUFFIX = ""

IMAGE_FSTYPES = "${INITRAMFS_FSTYPES}"
# local.conf appends "wic wic.bmap ext4" to IMAGE_FSTYPES for every image. An
# initramfs must only be a cpio — building a .wic here pulls in the kernel
# (do_image_wic -> linux-yocto:do_deploy), which the kernel's do_bundle_initramfs
# in turn depends on -> circular dependency. Strip those formats.
IMAGE_FSTYPES:remove = "wic wic.bmap ext4"
inherit image

# Keep it small; it rides bundled inside the signed bzImage.
IMAGE_ROOTFS_SIZE = "8192"
IMAGE_ROOTFS_EXTRA_SPACE = "0"
BAD_RECOMMENDATIONS += "busybox-syslog"
