SUMMARY = "netboot-prod /init wrapper: /data mount + persistent /etc overlay"
DESCRIPTION = "PID-1 shell wrapper installed as /init in eicke-image-netboot-prod \
(whose rootfs is the initramfs inside the signed UKI): mounts the plain on-disk \
data partition, lays the persistent /etc overlay on it, then execs systemd. The \
cpio image type only adds its /init -> /sbin/init symlink when /init is absent, \
so this file takes precedence."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://eicke-netboot-prod-init"

do_install() {
    install -m 0755 ${UNPACKDIR}/eicke-netboot-prod-init ${D}/init
}

FILES:${PN} = "/init"

# mount/mountpoint from busybox; findfs is NOT a default busybox applet, so
# pull util-linux-findfs like eicke-initramfs does; blkid + mkfs.ext4 are
# already part of the netboot image set but spelled out for correctness.
RDEPENDS:${PN} = "busybox util-linux-findfs util-linux-blkid e2fsprogs-mke2fs"
