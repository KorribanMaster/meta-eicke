# UEFI Secure Boot setup (eicke-image-prod)

Secure Boot for the production image is built with
[meta-secure-core](https://github.com/Wind-River/meta-secure-core) (wrynose
branch). The signed chain is **firmware (db) → shim (`bootx64.efi`) → SELoader →
grub → kernel**; SELoader publishes the MOK2/PKCS7 verify protocol grub uses to
authenticate `grub.cfg`, `boot-menu.inc` and the kernel.

## Layers (bblayers.conf)

Add, before `meta-eicke`:

    meta-secure-core/meta-signing-key
    meta-secure-core/meta-secure-core-common
    meta-secure-core/meta-efi-secure-boot
    meta-secure-core/meta-tpm2
    meta-secure-core/meta-encrypted-storage

`meta-efi-secure-boot` also depends on `meta-openembedded/meta-perl`.

## local.conf knobs

    DISTRO_FEATURES:append = " efi-secure-boot"
    UEFI_SELOADER = "1"        # shim + SELoader chain (provides grub's verifier)
    GRUB_SIGN_VERIFY = "0"     # mutually exclusive with UEFI_SELOADER=1
    SIGNING_MODEL = "sample"   # layer's public sample keys; use "user" for ours

Notes:
- `efi-secure-boot` is **distro-wide**: it patches grub to enforce signed
  configs/kernel and signs the kernel for every image. Only `eicke-image-prod`
  is wired for the signed-ESP boot flow today (its own `eicke-ab-prod.wks.in`);
  base/dev still use the bootimg-efi wks and would need migrating to boot under
  this distro feature.
- A no-shim / no-SELoader config fails at `shim_lock ... protocols not found`.

## What this layer's recipes do here

- `recipes-bsp/grub/grub-efi_%.bbappend` + `files/boot-menu.inc`: our A/B
  slot-selection + kernel cmdline, shadowing the layer's sample `boot-menu.inc`
  so it gets signed and verified.
- `recipes-core/images/eicke-image-prod.bb`: installs
  `packagegroup-efi-secure-boot` (shim, SELoader, grub-efi, efitools, mokutil)
  and uses `eicke-ab-prod.wks.in`.
- `recipes-core/images/eicke-image.bb`: the standalone `grubenv` seed is gated
  off when `efi-secure-boot` is set (grub-efi provides grubenv; avoids a deploy
  collision).

## QEMU verification

1. Build the SB firmware: `bitbake ovmf` → `ovmf.secboot.code.qcow2`.
2. Pre-enroll our PK/KEK/db into a raw varstore (e.g. with `virt-fw-vars`) from
   the keys in `meta-signing-key/files/uefi_sb_keys/` (sample) or our own keys,
   `--no-microsoft --secure-boot`.
3. Boot: `qemu ... -drive if=pflash,unit=0,readonly=on,file=ovmf.secboot.code.qcow2
   -drive if=pflash,unit=1,format=raw,file=<enrolled-vars> -drive file=<wic>,...`.

Verified: signed chain boots (guest SecureBoot on); a varstore with a different
db → firmware `Access Denied -- rejected by Secure Boot`; a tampered kernel →
grub `failed to verify kernel /boot/bzImage`.
