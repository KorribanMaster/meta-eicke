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
# Gated on the eicke-verified-boot distro feature (EICKE_VERIFIED_BOOT=1, see
# conf/distro/eicke.conf), so a non-verified qemuarm-uboot build is completely
# untouched — no FIT signing, no extra kernel deploy artifacts. Even with the
# feature on, nothing installs the signed FIT unless an image adds the
# linux-yocto-fitimage package (eicke-image-prod does, on ARM).

# Sign the FIT config node (kernel) without enabling UBOOT_SIGN_ENABLE — the
# latter would drive u-boot's own uboot-sign path (u-boot.dtb key embed).
FIT_KERNEL_SIGN_ENABLE = "${@bb.utils.contains('DISTRO_FEATURES', 'eicke-verified-boot', '1', '0', d)}"

# Key material: a fixed dev key in the shared key store (EICKE_KEYS_DIR /
# EICKE_FIT_KEYNAME from conf/distro/eicke.conf), mirroring the swupdate
# convention (host ~/yocto/keys mounted at /yocto/keys; swupdate-priv.pem lives
# there too). The FIT signer reads FIT_KERNEL_SIGN_KEYDIR/KEYNAME, which default
# from UBOOT_SIGN_KEYDIR/KEYNAME.
#
# Not auto-generated: kernel-signing-keys-native only generates when
# UBOOT_SIGN_ENABLE=1, which we must NOT set (see above). Generate the whole key
# store — including this FIT key — with scripts/eicke-gen-keys.sh, or by hand:
#   cd ~/yocto/keys
#   openssl genrsa -F4 -out eicke-fit.key 2048
#   openssl req -batch -new -x509 -key eicke-fit.key -out eicke-fit.crt -subj /CN=eicke-fit
UBOOT_SIGN_KEYDIR = "${EICKE_KEYS_DIR}"
UBOOT_SIGN_KEYNAME = "${EICKE_FIT_KEYNAME}"
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
# kernel-fit-image.bbclass to assemble the FIT from. Gated on the feature so a
# non-verified build's kernel is unchanged.
KERNEL_CLASSES:append = " ${@bb.utils.contains('DISTRO_FEATURES', 'eicke-verified-boot', 'kernel-fit-extra-artifacts', '', d)}"
