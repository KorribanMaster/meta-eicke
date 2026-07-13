# Verified boot: GRUB / iPXE / U-Boot

How the three production boot chains verify what they load, what they now share,
and what is still missing. Scope: the bootloader is the trust anchor — the
pre-bootloader chain (BootROM → SPL/firmware verifying the bootloader via SoC
fuses / UEFI SecureBoot db enrollment) is out of scope here.

## One switch

All three chains are turned on by a single knob in `local.conf`:

    EICKE_VERIFIED_BOOT = "1"

It sets the `eicke-verified-boot` distro feature (which every prod image and the
bootloader/signing recipes gate on) and, on x86, meta-secure-core's
`efi-secure-boot`. Combined with a populated key store
(`scripts/eicke-gen-keys.sh`), that is all a verified build needs. Without it the
prod images still build — hardened, but not verified.

## Where each chain stands

| | **GRUB** (x86 disk, `eicke-image-prod`) | **iPXE** (x86 netboot, `eicke-image-netboot-prod`) | **U-Boot** (ARM, `eicke-image-prod` on qemuarm-uboot) |
|---|---|---|---|
| Trust root | UEFI db → shim/MOK | UEFI db **directly** (no shim) | RSA pubkey in U-Boot control DTB |
| Chain | db → shim → SELoader → grub → signed bzImage + `.p7b` | db → signed `ipxe.efi` → HTTP → signed UKI (firmware `LoadImage` verifies) | (verified U-Boot) → signed kernel FIT via `bootm` |
| Signature format | PE/Authenticode + detached PKCS7 | PE/Authenticode (UKI + iPXE) | FIT signature node |
| Kernel unit | bzImage + `.p7b` | UKI (stub+bzImage+cpio+cmdline, `ukify --sign-kernel`) | fitImage (kernel; DTB external) |
| **Boot logic integrity** | signed `boot-menu.inc` (grub verifies) | firmware BootNext/BootOrder entries | **compiled-in `bootcmd`** (part of the U-Boot binary) |
| A/B unit | rootfs slots + `rootdev/ustate/bootcount` | iPXE-binary A/B via UEFI BootNext; OS is stateless RAM-boot | rootfs slots + `uboot.env` (same contract) |
| Confirm/rollback | `eicke-bootconfirm` clears `ustate` | `eicke-ipxeconfirm` promotes BootOrder | `eicke-bootconfirm` (shared script/lib) |
| Signing key | `sb-user/uefi_sb_keys` DB | **same** DB key | standalone `eicke-fit` RSA |

## What is common (by design)

- **One switch** (`EICKE_VERIFIED_BOOT`) and one gate (`eicke-verified-boot`) —
  the prod images and signing recipes all speak the same feature name.
- **One key store + one generator.** All keys live under `/yocto/keys`
  (`EICKE_KEYS_DIR`), created by `scripts/eicke-gen-keys.sh`. The key vocabulary
  is defined once in `conf/distro/eicke.conf`.
- **One A/B state contract** — `rootdev`/`ustate`/`bootcount`, the only mutable
  boot inputs, treated as untrusted data (`rootdev` canonicalized before it can
  reach the kernel cmdline; the U-Boot env whitelist enforces they are the only
  importable vars). See [eicke-update-image](images/eicke-update-image.md).
- **One confirm lifecycle** — `eicke-confirm-lib.sh` (shipped by
  `eicke-confirm-common`) holds the shared logging + trial-gate; the three
  backends (grubenv, uboot.env, UEFI BootOrder) are the only difference.
- **One update mechanism** — SWUpdate RSA-PSS-signed `.swu`, same `swupdate` key.
- **Mirrored boot logic** — `files/wic/grub.cfg` / `boot-menu.inc` and the
  U-Boot `CONFIG_BOOTCOMMAND` (eicke-bootcmd.cfg) are 1:1; fixes land as pairs.

## Who signs what, with which key

| Target | Signer | Key (`/yocto/keys`) | Format |
|---|---|---|---|
| `.swu` update bundle (all) | swupdate-signing.inc | `swupdate-priv.pem` | RSA-PSS over sw-description |
| x86 kernel/grub/shim/UKI/iPXE | meta-secure-core user-key-store | `sb-user/uefi_sb_keys/DB.{key,crt}` (+ mok/boot) | PE/Authenticode + PKCS7 |
| ARM kernel FIT | kernel-fit-image / uboot-sign | `eicke-fit.{key,crt}` | FIT signature node |

## Structural difference (why the three aren't one design)

Three legitimately different trust models: **firmware-rooted with a helper
chain** (GRUB: shim/SELoader bridge to the UEFI db), **firmware-rooted direct**
(iPXE: db verifies iPXE and the UKI, no shim), **bootloader-rooted** (U-Boot: the
key lives in U-Boot's own control DTB). GRUB verifies many *detached* signatures
as it walks the flow; iPXE defers image execution to the firmware's `LoadImage`;
U-Boot verifies one signed *container* (the FIT) in a single `bootm`. These can't
be unified — they follow the hardware. Everything *around* them now is.

## What is still missing on ARM

**Runtime FIT enforcement under QEMU.** `uboot-sign` embeds the verification
pubkey into the *built* `u-boot.dtb`, but qemu-virt hands U-Boot its control DTB
at runtime (`OF_BOARD`), so the FIT signature is verified best-effort, not
enforced. Path: qemu-virt honors `-dtb <file>` — dump the qemu DTB, insert the
`/signature` node (mkimage `-K`), feed it via `QB_OPT_APPEND`, mark the FIT conf
key `required`, drop `CONFIG_LEGACY_IMAGE_FORMAT`. On real hardware the key lives
in a fused/verified U-Boot's DTB and this disappears — emulator plumbing, the
same class as the swtpm limitation in
[encrypted-data-todo](encrypted-data-todo.md).

Out of scope: the pre-U-Boot chain (BootROM → SPL → U-Boot via SoC fuses),
hardware-specific.
