# TPM-sealed /data encryption — design + remaining work (WIP)

**Status:** design complete and the initramfs is implemented; the encrypt/unlock
+ `/etc`-overlay logic is written and the TPM/LUKS approach is verified-sound,
but the build-system mechanism that makes the *signed* kernel actually execute
the initramfs is not yet working. The production image currently ships the
verified Secure Boot + read-only-rootfs + `/etc`-overlay (preinit) configuration;
encryption is not yet active.

## Agreed design (confirmed with the user)

- Encrypt **`/data` only** (LUKS2). Rootfs stays plaintext read-only (integrity
  comes from Secure Boot). The persistent `/etc` overlay's upper dir lives on the
  encrypted `/data`.
- A **signed initramfs** runs before `switch_root`: it TPM-unseals the LUKS
  passphrase (sealed to **PCR 7** = Secure Boot state, via `cryptfs-tpm2`),
  `luksOpen`s `/data`, lays the `/etc` overlay on it, mounts the ro rootfs and
  `switch_root`s. This **replaces the `overlayfs-etc` preinit**.
- First boot provisions: `cryptfs-tpm2 seal` → `luksFormat` → `mkfs.ext4`.
- The TPM only releases the key when PCR 7 matches the sealed value, i.e. only
  after a successful Secure Boot with the enrolled keys.

## Implemented (kept in-tree as WIP, not yet wired into prod)

- `recipes-core/initrdscripts/eicke-initramfs-init.bb` + `files/eicke-initramfs-init`
  — the `/init` (provision/unseal/unlock `/data`, `/etc` overlay, `switch_root`).
- `recipes-core/images/eicke-initramfs.bb` — minimal initramfs image (busybox,
  cryptsetup, cryptfs-tpm2, util-linux-blkid/findfs, e2fsprogs-mke2fs, libtss2).
  Builds a ~24 MB `cpio.gz` containing `/init` and the crypto tooling.

## The blocker

`INITRAMFS_IMAGE_BUNDLE = "1"` does **not** embed our initramfs into the kernel
that lands at `/boot/bzImage` under meta-efi-secure-boot's kernel signing — the
deployed bzImage stays ~14 MB while the initramfs alone is ~24 MB, and a boot
shows `/init` never runs (`/data` stays plaintext, `/etc` not an overlay).

## Remaining work (next pass)

Switch to a **separate signed initrd** loaded by grub:
1. `INITRAMFS_IMAGE_BUNDLE = "0"`; build `eicke-initramfs` as a standalone cpio.gz.
2. Install the initrd into the prod rootfs `/boot/initrd` (per A/B slot, so grub
   on the active slot finds it) and **sign it** (SELoader PKCS7 `.p7b`, via
   `user-key-store` `sel_sign`) so grub's `initrd` command verifies it.
3. Add `initrd /boot/initrd` to the signed `boot-menu.inc`.
4. In prod: drop `overlayfs-etc` + its `OVERLAYFS_ETC_*`, switch `/data` to LUKS
   in `eicke-ab-prod.wks.in` (or leave ext4 and let the initramfs convert on
   first boot), ensure `/data` mountpoint exists in the ro rootfs.
5. Verify in qemu + swtpm (host has swtpm 0.10.1; emulate via
   `-chardev socket … -tpmdev emulator -device tpm-crb`): first boot provisions +
   seals; reboot unseals (PCR7 match) and `/data` persists; a wrong-PCR / SB-off
   boot fails the unseal and `/data` stays locked.

See [[secure-boot-meta-secure-core]] for the SB chain this builds on.
