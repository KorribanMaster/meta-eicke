# eicke-image

The bootable target image — a `core-image-minimal` derivative with the A/B
update stack.

- **Recipe:** `recipes-core/images/eicke-image.bb`
- **Build:** `bitbake eicke-image`
- **Artifacts:** `tmp/deploy/images/<machine>/eicke-image-<machine>.rootfs.wic`
  (full disk image) and `…rootfs.ext4` (the rootfs payload bundled into the
  `.swu`).

## Contents

- `packagegroup-core-boot` + `ssh-server-openssh` (from core-image-minimal +
  `IMAGE_FEATURES`).
- `swupdate` and `swupdate-www` — the update client and its local web UI.
- `grub-editenv`, `e2fsprogs`, `util-linux-blkid`, `libgcc` — runtime tooling
  used during updates.
- `eicke-bootconfirm` — systemd service that confirms a healthy boot
  (clears the SWUpdate trial state so an accepted slot persists).
- The kernel (`kernel-image`) installed into the rootfs, so each slot carries
  its own kernel.

## Disk layout (A/B)

Produced from `wic/eicke-ab.wks.in` (GPT):

| Part | Label | FS | Purpose |
|------|-------|----|---------|
| 1 | `esp`      | vfat | EFI System Partition: GRUB, `grub.cfg`, `grubenv` |
| 2 | `rootfs_a` | ext4 | rootfs slot A (populated at build time) |
| 3 | `rootfs_b` | ext4 | rootfs slot B (standby; written by SWUpdate) |
| 4 | `data`     | ext4 | persistent data / update state |

The image fstab mounts the ESP at `/boot` and the data partition at `/data` on
**every** slot, so the GRUB environment is always reachable.

## Notes

- Built with `EXTRA_IMAGE_FEATURES = "debug-tweaks"` → root has an empty
  password. Remove for production.
- See [doc/machines/qemux86-64.md](../machines/qemux86-64.md) to boot and the
  [eicke-update-image](eicke-update-image.md) page to update it.
