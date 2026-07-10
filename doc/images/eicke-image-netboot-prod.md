# eicke-image-netboot-prod

The Secure-Boot production variant of [eicke-image-netboot](eicke-image-netboot.md),
following [eicke-image-prod]'s hardening: the firmware verifies **iPXE** directly
(signed with our UEFI db key), iPXE chains a **signed UKI** (kernel + rootfs +
cmdline in one signed PE) that the firmware's `LoadImage` verifies, and a custom
`/init` gives the RAM rootfs persistence via a `/etc` overlay on the plain
on-disk `/data` partition. Credentials/SSH hardening is shared with
`eicke-image-prod` via `eicke-prod-hardening.inc`.

- **Recipes:** `recipes-core/images/eicke-image-netboot-prod.bb` (image),
  `recipes-core/images/eicke-netboot-prod-uki.bb` (signed UKI),
  `recipes-extended/images/eicke-update-image-netboot-prod.bb` (.swu)
- **Build:** `./build-netboot-prod.sh` (sets `EICKE_SECURE_BOOT=1`,
  MACHINE=qemux86-64; includes the `cleansstate grubenv` hack needed when the
  build dir previously did non-SB builds)
- **Artifacts:** `tmp/deploy/images/<machine>/`
  - `eicke-image-netboot-prod-<machine>.rootfs.wic` — disk (signed iPXE ESP + data)
  - `eicke-netboot-prod-uki.efi` — the signed UKI the netboot server serves
  - `ipxe.efi` — db-signed iPXE (`efi-unsigned/ipxe.efi` = unsigned, for tests)
  - `eicke-update-image-netboot-prod-<machine>.rootfs.swu` — signed iPXE update
  - `LockDown.efi` — on the ESP for first-time hardware key enrollment

## Secure Boot trust chain

```
UEFI firmware ── PK/KEK/db = OUR keys (SIGNING_MODEL=user, /keys/sb-user)
   │  verifies against db (sbsign, DB.key)
   ├─ EFI/BOOT/ipxe-a.efi │ ipxe-b.efi   ← A/B slots (BootNext one-shot trials)
   └─ EFI/BOOT/BOOTX64.EFI               ← fallback copy (first boot / lost NVRAM)
        │  dhcp → boot.ipxe → chain http://…/eicke-netboot-prod-uki.efi
        ▼
   signed UKI  =  systemd-boot stub + bzImage (also db-signed) + cpio.gz + cmdline
        │  firmware LoadImage verifies the WHOLE OS incl. cmdline
        ▼
   /init wrapper: mount /data (plain ext4) → overlay /etc → exec systemd
```

**Why no shim/SELoader (unlike eicke-image-prod):** shim chainloads a fixed
next-stage name, which is incompatible with the UEFI BootNext A/B scheme whose
entries point directly at `ipxe-a.efi`/`ipxe-b.efi`. Because we own the
platform keys, iPXE is signed straight into db — a shorter chain with the same
guarantees. (The public "you can't sign your own iPXE" caveat only applies to
the Microsoft/iPXE-CA ecosystem.)

**Why a UKI:** under Secure Boot iPXE defers image execution to the firmware's
`LoadImage`/`StartImage`; a plain bzImage+initrd cannot be verified that way.
The UKI signature covers kernel, initrd (= the entire rootfs) *and* the kernel
command line — changing any of them requires rebuilding and re-signing
(`bitbake eicke-netboot-prod-uki`). Re-serving a new UKI **is** the OS update
mechanism; only iPXE itself needs the on-device .swu path.

## Differences from eicke-image-netboot

| Aspect | netboot | netboot-prod |
|--------|---------|--------------|
| iPXE on ESP | unsigned | db-signed (firmware-verified) |
| OS delivery | bzImage + cpio.gz via boot.ipxe | one signed UKI via `chain` |
| Kernel cmdline | set by server boot.ipxe | baked + signed into the UKI |
| /data | fstab mount (nofail) | `/init` wrapper mounts it pre-systemd |
| /etc | volatile (RAM) | persistent overlay on /data |
| Credentials | dev (empty root pw) | real hashes, key-only root SSH |
| read-only-rootfs | n/a | not used — RAM rootfs is rebuilt from the signed UKI every boot; integrity comes from the signature, persistence only via /data |

The A/B iPXE update flow (staging, one-shot BootNext, `eicke-ipxeconfirm`
promotion, firmware fall-through) is identical to the base image — under
Secure Boot it is doubly failsafe: a corrupt or *tampered* slot additionally
fails signature verification (`Access Denied` in the firmware log) and the
firmware falls through BootOrder to the good slot. `eicke-ipxeconfirm` also
recreates entries whose device path no longer matches the ESP partition GUID
(reimaged disk).

## /data + /etc overlay (v1: plain, no LUKS/TPM)

`eicke-netboot-prod-init` is installed as `/init` (the cpio image type only
adds its `/init -> /sbin/init` symlink when `/init` is absent). It polls up to
10 s for the data partition (async device probing), creates the ext4 on first
boot, mounts it at `/data`, lays the overlay (`upper`/`work` under
`/data/overlay-etc/`) over `/etc` and execs systemd. Everything is
best-effort: without a disk the system still reaches multi-user, fully
volatile. LUKS2+TPM2 sealing (as wired in `eicke-initramfs` for prod) is a
follow-up once the swtpm blocker is resolved — the shipped `eicke-image-prod`
runs without the TPM path active today, so v1 matches prod's actual state.

## Verifying in QEMU

`meta-eicke/scripts/eicke-netboot-prod-sim.sh` wraps the whole setup: it
enrolls PK/KEK/db into a private OVMF varstore once (`virt-fw-vars`, venv at
`workspace/.vfw-venv` — run the enrollment on the host if the venv's python
doesn't exist in the container), serves the deploy dir over HTTP, hands iPXE a
`boot.ipxe` that chains the UKI, and boots the Yocto QEMU with
`ovmf.secboot.code.qcow2`. Delete `$SIM/ovmf.vars.sb.qcow2` to simulate a
factory-fresh device.

Verified checklist (all exercised on 2026-07-10):

1. **Positive boot:** signed iPXE (fallback path on first boot) → chain signed
   UKI → `EICKE-NETBOOT-INIT` wrapper → hardened systemd. In the guest:
   SecureBoot efivar = 1, kernel logs `Secure boot enabled`, `overlay on /etc`,
   `ipxe-a`/`ipxe-b` entries fronting BootOrder, no `LABEL=data` fstab line.
2. **Persistence + hardening:** `/etc` marker survives reboot; root SSH by key
   only (`sshpass -p '' … → Permission denied`).
3. **Update round-trip under SB:** upload the .swu → `SWUPDATE successful`,
   BootNext trial boots the new slot, `eicke-ipxeconfirm` promotes it.
4. **NEGATIVE — unsigned iPXE:** write `efi-unsigned/ipxe.efi` over a slot,
   BootNext at it → firmware: `failed to load Boot0004 "ipxe-a" … Access
   Denied -- rejected probably by Secure Boot` → falls through to the signed
   slot; healing = re-apply the .swu.
5. **NEGATIVE — tampered UKI:** serve a byte-flipped UKI → iPXE `Could not
   boot: Error 0x7f04818f` (LoadImage access denied) → embedded retry loop
   holds the box safe (with `imgfree` per retry — without it, repeated ~80 MB
   downloads exhaust iPXE's heap); restoring the good UKI boots on the next
   retry without intervention.

## Hardware enrollment

Follow `~/yocto/keys/sb-user/fw-enroll/ENROLL-README.md` (BIOS Custom-mode
enrollment of `DB.cer`/`KEK.cer`/`PK.cer`), or run `EFI/BOOT/LockDown.efi`
from the firmware boot menu while in Setup Mode. The netboot server must serve
`eicke-netboot-prod-uki.efi` and a `boot.ipxe` with the single `chain` line.

[eicke-image-prod]: ../../recipes-core/images/eicke-image-prod.bb
