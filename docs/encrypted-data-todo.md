# TPM-sealed /data encryption — design + remaining work (WIP)

**Status:** the entire boot/initramfs pipeline is implemented and QEMU-verified
to work end-to-end EXCEPT the final TPM seal. The signed separate initrd loads
and is SELoader-verified, the kernel unpacks it, `/init` runs, finds the rootfs
and data partitions, and reaches the TPM provisioning step. The ONLY failure is
`cryptfs-tpm2`'s `TPM2_Create` of the sealed passphrase object on the swtpm
emulator (`RC 0x18b` = FMT1 handle-1 `TPM_RC_KEY` — it hardcodes an RSA primary
key that swtpm rejects as a parent). This is very likely a swtpm-emulator quirk
(cryptfs-tpm2 is a Wind River production tool used with real hardware TPMs), but
it blocks the qemu verification. The production image therefore currently ships
the verified Secure Boot + read-only-rootfs + `/etc`-overlay (preinit) config;
encryption is wired but de-activated pending this fix.

## What is verified working (this is the bulk of it)

- Separate signed initrd: `kernel-initramfs` installs the eicke-initramfs cpio at
  `/boot/initrd` (+ real-file deref) and meta-efi-secure-boot signs it
  (`/boot/initrd.p7b`). grub's `initrd` line loads it; under SELoader/mok2verify
  grub uses the legacy initrd path (`grub_is_secured()` → boot_params), and the
  kernel unpacks it ("Freeing initrd memory: 23892K").
- `/init` runs as PID 1: resolves `root=PARTLABEL=rootfs_a` and the data
  partition, sets `TSS2_TCTI=device`, and reaches TPM provisioning. OVMF exposes
  the TPM2 ACPI table; cryptfs-tpm2 talks to the TPM (reads/votes PCR banks).

## The one blocker

`cryptfs-tpm2 -q seal passphrase -P sha256` →
`[ERROR] Unable to create the passphrase object (0x18b)` (create.c:524). 0x18b =
TPM_RC_KEY on the parent handle of `TPM2_Create`. cryptfs-tpm2's
`cryptfs_tpm2_create_primary_key()` hardcodes `set_public(TPM2_ALG_RSA, …)` with
no option to change it.

## Remaining work (next pass) — pick one

1. **Patch cryptfs-tpm2** to use an ECC primary key (or fix the RSA storage-key
   attributes) so swtpm accepts it as a parent — a small bbappend patch to
   `src/lib/create.c`. Then re-run the qemu+swtpm two-boot test.
2. **Switch to `systemd-cryptenroll`** (the standard, robust TPM2-LUKS tool):
   in `/init`, `luksFormat` `/data` with a random key, `systemd-cryptenroll
   --tpm2-device=auto --tpm2-pcrs=7`, then `cryptsetup luksOpen` via the TPM2
   token. Needs libcryptsetup TPM2 support + the token handler in the initramfs.
3. Or verify on **real hardware** (the failure is likely swtpm-specific).

To re-activate the initramfs encryption path in eicke-image-prod.bb (the recipes
`eicke-initramfs.bb` and `eicke-initramfs-init.bb` + `/init` are committed; the
prod wiring below was reverted to keep prod bootable and must be re-applied):

- drop `overlayfs-etc` from `inherit` and from `IMAGE_FEATURES` (+ remove the
  `OVERLAYFS_ETC_*` vars and the `eicke_prod_drop_data_fstab` /data-line strip);
- `IMAGE_INSTALL:append = " kernel-initramfs"`; create `/data` as a mountpoint in
  the ro rootfs; add a postprocess to deref `/boot/initrd` + `/boot/initrd.p7b`
  from symlinks to real files (grub's SELoader initrd path needs real files);
- in local.conf: `INITRAMFS_IMAGE = "eicke-initramfs"`, `INITRAMFS_IMAGE_BUNDLE = "0"`;
- in boot-menu.inc add `initrd /boot/initrd` after the `linux` line.

(`/init` currently force-logs to `/dev/ttyS0` for diagnostics — make that
conditional/remove for production.)

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
