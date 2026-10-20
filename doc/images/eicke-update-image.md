# eicke-update-image

The SWUpdate **update bundle** (`.swu`) used to update a running `eicke-image`
A/B device.

- **Recipe:** `recipes-extended/images/eicke-update-image.bb`
- **Build:** `bitbake eicke-update-image` (depends on `eicke-image`)
- **Artifact:** `tmp/deploy/images/<machine>/eicke-update-image-<machine>.rootfs.swu`

## What's inside

A `.swu` is a cpio stream containing, in order:

1. `sw-description` — the update manifest
   (`recipes-extended/images/eicke-update-image/sw-description`).
2. `eicke-image-<machine>.rootfs.ext4` — the new rootfs (with its kernel),
   `sha256`-verified by SWUpdate before being written.

## How it updates (dual-copy)

The `sw-description` embeds a Lua hook (`eicke_set_standby`) that picks the
**standby** slot automatically at install time: it reads the running slot from
`/proc/cmdline` (`eicke.slot=rootfs_X`), writes the new rootfs to the *other*
slot, and sets grubenv (`rootdev=<standby>`, `ustate=1`, `bootcount=0`). Because
no selection is required, the **same bundle installs both ways**:

```sh
# CLI, on the device (no -e needed):
swupdate -i eicke-update-image-<machine>.rootfs.swu && reboot

# Web interface (SWUpdate mongoose server, port 8080):
curl -F filename=@eicke-update-image-<machine>.rootfs.swu http://<target>:8080/upload
# then reboot from the web UI or `reboot`
```

The standby slot is written through `/dev/eicke/<slot>`, not
`/dev/disk/by-partlabel/`. A board may carry a second disk whose GPT has the
same partition names (the ccimx8mn DVK's eMMC with a vendor A/B install, for
example), and a by-name lookup can then resolve to that disk. `eicke-bootpart`
(`recipes-support/eicke-bootpart`) and its udev rule create `/dev/eicke/{esp,
rootfs_a,rootfs_b,data}` only for partitions on the disk that holds the
running root. For the same reason every bootloader passes
`root=PARTUUID=<slot partition>` (resolved from its own boot disk) plus
`eicke.slot=`, the fstab mounts `/dev/eicke/esp` and `/dev/eicke/data`, and the
prod overlayfs-etc preinit resolves `/data` with `eicke-bootpart data`.

On reboot the bootloader boots the new slot. The `eicke-bootconfirm` service
then clears `ustate`; if the new slot never reaches that point, the bootcount
logic rolls back to the previous slot.

## Hardware-compatibility gating

swupdate is built with `CONFIG_HW_COMPATIBILITY=y`: it compares the second
field of the device's `/etc/hwrevision` (shipped by `eicke-hwrevision` as the
bitbake `MACHINE`) against the `hardware-compatibility` list in the bundle's
sw-description (expanded from `@@MACHINE@@`). A bundle built for another
machine is refused before anything is written.

**Migration**: a swupdate binary from after this change rejects *older*
bundles (they carry no `hardware-compatibility` attribute), while older
binaries silently ignore the new attribute. So when updating a fielded
device, new-style bundles install fine on old systems — but once a device
runs the new image, only new-style bundles are accepted.

## The A/B state contract

Exactly three mutable variables cross the trust boundary between userspace and
the bootloader — on x86 in `grubenv` on the ESP, on qemuarm-uboot in
`uboot.env` on the boot partition:

| Variable | Values | Meaning |
|---|---|---|
| `rootdev` | `rootfs_a` \| `rootfs_b` | slot to boot |
| `ustate` | `0` ok, `1` trial, `3` failed | SWUpdate trial state |
| `bootcount` | `0` \| `1` | trial boot counter |

They are the *only* boot inputs that may change at runtime, and the boot logic
(`grub.cfg`/`boot-menu.inc` and the U-Boot `CONFIG_BOOTCOMMAND`, kept as 1:1 mirrors) treats them as
untrusted data: `rootdev` is canonicalized to a literal before use, so env
content can never reach the kernel command line. Writers are SWUpdate's
bootloader handler and `eicke-bootconfirm` (plus the bootloader's own trial
bookkeeping); nothing else should touch these variables.

See [doc/machines/qemux86-64.md](../machines/qemux86-64.md) (GRUB) or
[doc/machines/qemuarm-uboot.md](../machines/qemuarm-uboot.md) (U-Boot) for a
full apply-and-verify walkthrough under QEMU.

## Bootloader updates

The rootfs bundles never touch the bootloader. On te0820, where the
bootloader (`boot.bin`, `u-boot.itb`) lives once on the ESP rather than per
slot, it has a bundle of its own, `eicke-update-bootloader`: same signing
key and hardware-compatibility gate, files written as `*.new` and swapped in
by a script (keeping `*.old`), and `bootloader_state_marker = false` /
`bootloader_transaction_marker = false`, so the A/B state above is left as it
is. See [doc/machines/te0820.md](../machines/te0820.md#updating-with-swupdate).
