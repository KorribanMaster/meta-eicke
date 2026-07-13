# Signed kernel FIT for qemuarm-uboot — BUILD-AND-BOOT, ENFORCEMENT DEFERRED.
#
# Enables FIT kernel-image signing (linux-yocto-fitimage) so a production image
# can ship a signed /boot/fitImage per A/B slot. Runtime signature ENFORCEMENT
# is deliberately deferred: under QEMU virt U-Boot takes its control DTB from
# the emulator at runtime (OF_BOARD), so the verification pubkey that
# uboot-sign would embed into the built u-boot.dtb is never consulted. We
# therefore enable ONLY the FIT-side signing (FIT_KERNEL_SIGN_ENABLE) and
# leave UBOOT_SIGN_ENABLE at 0 — u-boot.inc always inherits uboot-sign, and
# turning it on would run the pointless (and fragile) u-boot.dtb key-embed /
# -dtb path. The signed FIT then best-effort-verifies against qemu's key-less
# runtime DTB, warns, and boots. On real hardware the DTB ships inside the FIT
# and the key lives in a U-Boot the SoC verified, closing the loop.
# See doc/verified-boot.md.
#
# This makes the machine FIT-capable; nothing installs the signed FIT unless an
# image adds the linux-yocto-fitimage package (eicke-image-prod does, on ARM).

# Sign the FIT config node (kernel) without enabling UBOOT_SIGN_ENABLE — the
# latter would drive u-boot's own uboot-sign path (u-boot.dtb key embed).
FIT_KERNEL_SIGN_ENABLE = "1"

# Key material: a fixed dev key in the shared key store, mirroring the swupdate
# convention (host ~/yocto/keys mounted at /yocto/keys in the build container;
# swupdate-priv.pem already lives there). The FIT signer reads
# FIT_KERNEL_SIGN_KEYDIR/KEYNAME, which default from UBOOT_SIGN_KEYDIR/KEYNAME.
#
# Not auto-generated: kernel-signing-keys-native only generates when
# UBOOT_SIGN_ENABLE=1, which we must NOT set (see above). Create the key once:
#   cd ~/yocto/keys
#   openssl genrsa -F4 -out eicke-fit.key 2048
#   openssl req -batch -new -x509 -key eicke-fit.key -out eicke-fit.crt -subj /CN=eicke-fit
EICKE_KEYS_DIR ??= "/yocto/keys"
UBOOT_SIGN_KEYDIR = "${EICKE_KEYS_DIR}"
UBOOT_SIGN_KEYNAME = "eicke-fit"
FIT_SIGN_ALG = "rsa2048"
FIT_HASH_ALG = "sha256"

# Kernel load/entry embedded in the FIT. The oe default (0x20008000, from
# kernel.bbclass) is below qemu virt's RAM base (0x40000000), so bootm fails
# with "Unable to allocate memory ... Boot failed (err=-14)". Use base+0x8000.
# boot.cmd loads the FIT itself higher (0x41000000) so this copy destination
# (0x40008000 + ~8 MiB kernel) does not overlap the FIT blob. (zImage/bootz is
# self-relocating and ignores these, so the base image is unaffected.)
UBOOT_LOADADDRESS = "0x40008000"
UBOOT_ENTRYPOINT = "0x40008000"

# linux-yocto (virtual/kernel) must deploy linux.bin + linux_comp for
# kernel-fit-image.bbclass to assemble the FIT from.
KERNEL_CLASSES:append = " kernel-fit-extra-artifacts"
