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

The `sw-description` defines two modes; pick the one for the **standby** slot:

| Mode | Writes to | Sets in grubenv |
|------|-----------|-----------------|
| `stable,copy1` | `rootfs_b` (`/dev/disk/by-partlabel/rootfs_b`) | `rootdev=rootfs_b`, `ustate=1` |
| `stable,copy2` | `rootfs_a` | `rootdev=rootfs_a`, `ustate=1` |

Apply it on the device (run `copy1` while booted on A, `copy2` while on B):

```sh
swupdate -i eicke-update-image-<machine>.rootfs.swu -e stable,copy1
reboot
```

On reboot GRUB boots the new slot. The `eicke-bootconfirm` service then clears
`ustate`; if the new slot never reaches that point, GRUB's bootcount logic rolls
back to the previous slot.

See [doc/machines/qemux86-64.md](../machines/qemux86-64.md) for a full
apply-and-verify walkthrough under QEMU.
