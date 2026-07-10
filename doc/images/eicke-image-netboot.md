# eicke-image-netboot

A netboot variant of [eicke-image](eicke-image.md) for `qemux86-64` where
**iPXE is the bootloader**: the disk carries only iPXE and a data partition,
and the OS (kernel + rootfs) is fetched over the network on every boot and
runs from RAM. SWUpdate stays in the image, repurposed as a **failsafe A/B
updater for iPXE itself** using UEFI boot-manager entries + `BootNext`.
No zynq packages, no GRUB, no rootfs on disk.

- **Recipe:** `recipes-core/images/eicke-image-netboot.bb`
- **Build:** `bitbake eicke-image-netboot` (and `bitbake
  eicke-update-image-netboot` for the iPXE update bundle)
- **Artifacts:** `tmp/deploy/images/<machine>/`
  - `eicke-image-netboot-<machine>.rootfs.wic` — the disk (iPXE ESP + data)
  - `eicke-image-netboot-<machine>.rootfs.cpio.gz` — the rootfs the netboot
    server serves as initramfs
  - `ipxe.efi` — the bare iPXE binary (from the `ipxe` recipe)
  - `eicke-update-image-netboot-<machine>.rootfs.swu` — signed iPXE update

## Boot architecture

```
UEFI firmware (NVRAM: BootOrder, BootNext)
  ├─ Boot entry "ipxe-a" → ESP:\EFI\BOOT\ipxe-a.efi   ← A/B slots, managed by
  ├─ Boot entry "ipxe-b" → ESP:\EFI\BOOT\ipxe-b.efi     SWUpdate + eicke-ipxeconfirm
  └─ firmware fallback   → ESP:\EFI\BOOT\BOOTX64.EFI  ← first boot only (before
       │                                                 the entries exist)
       ▼
  iPXE (embedded script: dhcp → autoboot, retry forever)
       │  DHCP hands out `bootfile` (e.g. boot.ipxe)
       ▼
  boot.ipxe: fetch bzImage + eicke-image-netboot….cpio.gz over HTTP → boot
       ▼
  systemd runs from the initramfs in RAM (no switch_root, no disk rootfs)
```

The OS itself is updated by changing what the netboot server serves — only
iPXE needs an on-device update path.

## Disk layout

Produced from `wic/eicke-netboot.wks` (GPT, exactly two partitions):

| Part | Label  | FS   | Purpose |
|------|--------|------|---------|
| 1    | `esp`  | vfat | `EFI/BOOT/BOOTX64.EFI`, `EFI/BOOT/ipxe-a.efi`, `EFI/BOOT/ipxe-b.efi` |
| 2    | `data` | ext4 | persistent data |

All three ESP files start out as the same iPXE build. `BOOTX64.EFI` is the
UEFI removable-media fallback path: on the very first boot no `ipxe-a`/`ipxe-b`
NVRAM entries exist yet, so the firmware's auto-generated disk entry loads it.
It is never updated — it is the last-resort safety net (and the recovery path
if NVRAM is ever lost).

The image's own fstab mounts the ESP at `/boot` and the data partition at
`/data` by filesystem label (both `nofail` — a netbooted system still reaches
`multi-user.target` without the disk).

## iPXE A/B update (UEFI BootNext)

Components:

- `eicke-ipxeconfirm` (systemd oneshot, every boot):
  1. mounts `efivarfs` (systemd does not mount it when running straight from
     an initramfs) and locates the ESP via `blkid -L esp`;
  2. creates missing `ipxe-a`/`ipxe-b` UEFI entries pointing at the ESP files
     (first boot), and recreates entries whose device path no longer matches
     the current ESP partition GUID (reimaged disk);
  3. **confirmation**: puts the currently running ipxe entry first in
     `BootOrder` (all foreign entries are preserved after the pair).
- `eicke-update-image-netboot.swu` (RSA-PSS signed, same key as the rootfs
  bundles):
  1. `rawfile`: streams the new `ipxe.efi` to `/boot/EFI/BOOT/ipxe-staged.efi`
     — never directly over a bootable slot, so a mid-write power cut cannot
     corrupt one;
  2. postinstall script: determines the standby slot from `efibootmgr`
     `BootCurrent`, moves the staged file over the standby `ipxe-{a,b}.efi`,
     and arms a **one-shot** trial: `efibootmgr --bootnext <standby>`.

Update lifecycle:

```
running slot A                       standby slot B
     │  swupdate: stage + mv → ipxe-b.efi, BootNext=ipxe-b
     ▼
 reboot ── firmware consumes BootNext ──► boots ipxe-b (trial)
     │                                        │ OS reaches userspace
     │                                        ▼
     │                     eicke-ipxeconfirm: BootOrder = ipxe-b,ipxe-a,…
     │                                        (update accepted, persistent)
     └─ trial fails to load / hangs → reset → BootNext already consumed
                                    → firmware boots BootOrder[0] = ipxe-a
                                    (old slot, automatic rollback; a corrupt
                                    binary is skipped by the firmware even
                                    within the same boot via BootOrder)
```

Install via CLI or the SWUpdate web interface (port 8080), like the rootfs
bundles:

```sh
swupdate -i eicke-update-image-netboot-<machine>.rootfs.swu
curl -F filename=@eicke-update-image-netboot-<machine>.rootfs.swu http://<target>:8080/upload
```

The sw-description sets `bootloader_transaction_marker/state_marker = false`:
swupdate's compiled-in GRUB bootenv backend (shared build with the rootfs
images) must not try to persist state in a grubenv that does not exist here —
trial/rollback state lives entirely in the UEFI variables.

### Limitations

- A new iPXE that **loads but then hangs** is not rolled back until the next
  reset (BootNext is consumed at load time, so the reset lands on the old
  slot). Pair with a hardware watchdog on real boards.
- The netbooted rootfs is stateless (fresh machine-id etc. per boot); use
  `/data` for anything persistent.

## Netboot server contract

iPXE DHCPs and honors the DHCP `bootfile`/next-server (standard `autoboot`).
The boot script must load the deployed kernel and the image's cpio.gz, e.g.:

```
#!ipxe
kernel http://<server>:8000/bzImage console=ttyS0 rdinit=/sbin/init
initrd http://<server>:8000/eicke-image-netboot-qemux86-64.rootfs.cpio.gz
boot
```

Serve `bzImage` and the `.cpio.gz` from `tmp/deploy/images/<machine>/` (they
must come from the same build so kernel modules in the rootfs match). Give the
machine RAM for kernel + unpacked rootfs + page cache; 2 GiB is comfortable.

## Verifying in QEMU (Yocto-built qemu via runqemu)

All inside the build container (`EICKE_DOCKER_QEMU=1 …/dock.sh`, then
`. ./setup-environment`); `bitbake eicke-image-netboot eicke-update-image-netboot ovmf qemu-system-native qemu-helper-native` first.
`meta-eicke/scripts/eicke-netboot-sim.sh` wraps steps 1–3 below.

```sh
DEPLOY=$BUILDDIR/tmp/deploy/images/qemux86-64
SIM=$BUILDDIR/netboot-sim && mkdir -p $SIM

# 1. boot script + OVMF with a private, PERSISTENT vars copy (NVRAM must
#    survive reboots and qemu restarts; basename must start with "ovmf")
printf '#!ipxe\nkernel http://10.0.2.2:8000/bzImage console=ttyS0 rdinit=/sbin/init\ninitrd http://10.0.2.2:8000/eicke-image-netboot-qemux86-64.rootfs.cpio.gz\nboot\n' > $SIM/boot.ipxe
cp $DEPLOY/ovmf.code.qcow2 $SIM/
cp $DEPLOY/ovmf.vars.qcow2 $SIM/ovmf.vars.netboot.qcow2   # once; keep across runs

# 2. serve kernel + initramfs (10.0.2.2 = the host in slirp)
(cd $DEPLOY && python3 -m http.server 8000 &)

# 3. boot — QB_SLIRP_OPT wires the DHCP bootfile + port forwards
QB_SLIRP_OPT="-netdev user,id=net0,tftp=$SIM,bootfile=boot.ipxe,hostfwd=tcp:127.0.0.1:8080-:8080,hostfwd=tcp:127.0.0.1:2222-:22" \
runqemu $DEPLOY/eicke-image-netboot-qemux86-64.rootfs.wic slirp kvm nographic \
    $SIM/ovmf.code.qcow2 $SIM/ovmf.vars.netboot.qcow2 qemuparams="-m 2048"
```

Checklist (each step verified against this setup):

1. **First boot:** iPXE banner → DHCP → boot.ipxe → HTTP kernel/initramfs →
   login. In the guest, `efibootmgr` shows `ipxe-a`/`ipxe-b` entries with
   `BootOrder` starting `ipxe-a,ipxe-b`.
2. **Update:** `curl -F filename=@….swu http://localhost:8080/upload` →
   journal shows `SWUPDATE successful`, `BootNext` = standby entry, standby
   file replaced. `reboot` → new iPXE runs → `BootOrder` now leads with it.
   Another `reboot` stays on the new slot.
3. **Rollback:** apply the update again, then corrupt the standby before
   rebooting (`dd if=/dev/zero of=/boot/EFI/BOOT/ipxe-<standby>.efi bs=4096
   count=16; sync` — note busybox dd truncates, which is the point) →
   `reboot` → firmware fails to load the trial entry, falls through to the
   old slot; `BootOrder` unchanged, `BootNext` gone. Re-applying the update
   heals the corrupt slot.
