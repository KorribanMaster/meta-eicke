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

On reboot GRUB boots the new slot. The `eicke-bootconfirm` service then clears
`ustate`; if the new slot never reaches that point, GRUB's bootcount logic rolls
back to the previous slot.

See [doc/machines/qemux86-64.md](../machines/qemux86-64.md) for a full
apply-and-verify walkthrough under QEMU.
