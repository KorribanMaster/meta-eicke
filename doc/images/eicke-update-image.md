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
`/proc/cmdline` (`root=PARTLABEL=rootfs_X`), writes the new rootfs to the *other*
slot, and sets grubenv (`rootdev=<standby>`, `ustate=1`, `bootcount=0`). Because
no selection is required, the **same bundle installs both ways**:

```sh
# CLI, on the device (no -e needed):
swupdate -i eicke-update-image-<machine>.rootfs.swu && reboot

# Web interface (SWUpdate mongoose server, port 8080):
curl -F filename=@eicke-update-image-<machine>.rootfs.swu http://<target>:8080/upload
# then reboot from the web UI or `reboot`
```

On reboot the bootloader boots the new slot. The `eicke-bootconfirm` service
then clears `ustate`; if the new slot never reaches that point, the bootcount
logic rolls back to the previous slot.

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
