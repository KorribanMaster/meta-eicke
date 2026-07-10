# Verified boot: status and roadmap (ARM/U-Boot vs x86/GRUB)

Evaluation of how far the two boot chains verify what they load, what is still
missing on ARM, and how to keep the two approaches as common as possible.
Scope note: **U-Boot itself is taken as the trust anchor** — provisioning the
SoC to only run a signed U-Boot (fuses/BootROM) is out of scope here; this is
about what happens *after* U-Boot starts.

## Where each chain stands

| Stage | x86 (`eicke-image-prod`, GRUB) | ARM (`qemuarm-uboot`) |
|---|---|---|
| Bootloader → OS | UEFI Secure Boot: shim → SELoader → grub → signed kernel + `.p7b` (meta-secure-core) | **not verified yet** — `boot.scr` + raw `zImage` via `bootz` |
| Boot logic integrity | signed `boot-menu.inc` (grub verifies) | ⚠ `boot.cmd` compiled into `boot.scr`, unsigned on FAT |
| Mutable A/B state | `grubenv`, data-only by construction | `uboot.env`, **contained** to the 3 contract vars (done) |
| Cmdline injection via `rootdev` | fixed (canonicalized) | fixed (canonicalized) |
| Update bundle | SWUpdate RSA-PSS signed | SWUpdate RSA-PSS signed (same) |

Two hardening items already landed and apply to **both** bootloaders:

1. **rootdev canonicalization** (`a6996c2`) — env content can no longer reach
   the kernel command line on either bootloader.
2. **U-Boot env containment** (`f74f24f`) — `CONFIG_ENV_WRITEABLE_LIST` +
   `CFG_ENV_FLAGS_LIST_STATIC` whitelist so a tampered `uboot.env` cannot inject
   `bootcmd`/`bootargs`. This closes the gap that `grubenv` never had (grubenv
   feeds data into *signed* grub logic; `uboot.env` replaces the whole env).

## What is still missing on ARM (in priority order)

1. **Signed kernel FIT.** Package the kernel (+DTB where applicable) as a FIT
   image, sign it, and load it with `bootm` instead of the raw `bootz zImage`.
   `CONFIG_FIT_SIGNATURE=y` is already in `qemu_arm_defconfig`; oe-core ships
   the machinery (`kernel-fit-image.bbclass`, `uboot-sign.bbclass`,
   `linux-yocto-fitimage.bb`). Mark the config key `required` and drop
   `CONFIG_LEGACY_IMAGE_FORMAT` so an unsigned/raw image is refused.
2. **Signed boot logic.** Move the A/B logic from the FAT `boot.scr` into the
   **compiled-in default `bootcmd`** (an env fragment) — the analog of x86's
   signed `boot-menu.inc`. Logic then changes only via the controlled U-Boot
   artifact, not via FAT contents. (Alternative: wrap the script in a signed
   FIT — more machinery, same result.)
3. **Key trust under QEMU (the real blocker).** `uboot-sign` embeds the FIT
   verification pubkey into the *built* `u-boot.dtb`. But qemu-virt hands U-Boot
   its control DTB at runtime (`OF_BOARD`; our `boot.cmd` already boots with
   `${fdtcontroladdr}`), so the built DTB — and its key — is never consulted.
   **Path around it:** qemu-virt honors `-dtb <file>`, so dump the qemu DTB
   once, insert the `/signature` node (the same mkimage `-K` step uboot-sign
   runs on `u-boot.dtb`), and pass that key-bearing DTB to qemu via
   `QB_OPT_APPEND`. On real hardware the DTB ships inside the signed FIT and
   this whole wrinkle disappears — it is emulator plumbing, the same class of
   issue as the swtpm limitation in [encrypted-data-todo](encrypted-data-todo.md).
4. **Key material.** One FIT RSA keypair under the existing `/yocto/keys` store,
   referenced by `UBOOT_SIGN_KEYDIR`/`UBOOT_SIGN_KEYNAME`.

Not attempted here and explicitly out of scope: the pre-U-Boot chain (BootROM →
SPL → U-Boot verification via SoC fuses), which is hardware-specific.

## GRUB vs U-Boot — structural comparison

| Aspect | x86 GRUB (meta-secure-core) | ARM U-Boot |
|---|---|---|
| Root of trust | UEFI db + shim/MOK certs | RSA pubkey in U-Boot's control DTB |
| Signature format | Authenticode + SELoader PKCS7 (`.p7b` per file) | FIT signature nodes (one signed container) |
| Boot logic | signed text config (`boot-menu.inc`) | compiled-in `bootcmd` (proposed) |
| Kernel artifact | `bzImage` + `.p7b` | `fitImage` (kernel [+dtb]) |
| Enforcement | grub patched to require signatures | `CONFIG_FIT_SIGNATURE` + `required` conf key + no legacy format |
| Mutable state | `grubenv` (data-only by construction) | `uboot.env` (constrained by whitelist) |
| DTB | n/a (ACPI/EFI) | in FIT (real hw) / key-injected via qemu `-dtb` (emulation) |

Fundamental difference: GRUB verifies **many detached signatures** over
individual files as it walks the boot flow; U-Boot verifies **one signed
container** (the FIT) in a single `bootm`. GRUB's chain is longer (shim/SELoader
exist to bridge to UEFI db); U-Boot's is shorter because U-Boot *is* the
verifier and its key is baked into its control DTB.

## Advice: maximizing commonality

1. **One abstract switch — `eicke-verified-boot`** distro feature — mapping to
   `efi-secure-boot` on x86 and to `UBOOT_SIGN_ENABLE`/FIT on ARM. Gate
   `eicke-image-prod.bb` and the `eicke-ab-*.inc` pair on this single feature
   instead of naming `efi-secure-boot` directly, so "prod = verified" reads the
   same on both machines.
2. **One key store.** FIT keypair lives beside the Secure Boot keys under
   `/yocto/keys`; extend [secure-boot-setup](secure-boot-setup.md) with a single
   provisioning section covering both chains.
3. **One A/B state contract** (`rootdev`/`ustate`/`bootcount`, documented in
   [eicke-update-image](images/eicke-update-image.md)) — already the case;
   both bootloaders treat these as untrusted data, and the U-Boot whitelist now
   enforces that they are the *only* mutable boot inputs.
4. **Keep `boot.cmd` and `grub.cfg`/`boot-menu.inc` mirrored 1:1** (they are —
   cross-referencing header comments make edits land as pairs).
5. **Bug fixes land on both bootloaders in one commit** (as the rootdev fix
   did) — the mirrored logic makes this natural and prevents drift.

## Remaining work if the FIT chain is pursued

1. `eicke-verified-boot` feature plumbing (x86 → efi-secure-boot; ARM → FIT).
2. FIT keypair into `/yocto/keys`; wire `kernel-fit-image` + `uboot-sign`
   (`UBOOT_SIGN_ENABLE=1`, keydir/keyname, `FIT_SIGN_ALG`).
3. Per-slot `/boot/fitImage`; `boot.cmd`/compiled `bootcmd` loads it with
   `bootm`; drop `bootz` + legacy image format.
4. qemu key-injection: dump virt DTB, add `/signature`, feed via `-dtb`.
5. Re-verify A/B + rollback with enforcement on, plus a **negative test**:
   a one-byte-tampered `fitImage` must be refused by U-Boot.
