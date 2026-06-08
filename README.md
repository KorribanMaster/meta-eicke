# meta-eicke

A Yocto layer (scarthgap / 5.0 LTS) that builds a basic poky-based x86-64 image
with a **GRUB-EFI** bootloader, a custom **A/B (dual-copy) WIC disk image**, and
**SWUpdate** for image updates.

It is normally consumed through the
[`eicke-manifest`](https://github.com/KorribanMaster/eicke-manifest) `repo`
manifest, which also provides the Docker build environment.

## Dependencies

`LAYERSERIES_COMPAT = scarthgap`. Layer dependencies:

- `core` (poky / `meta`, `meta-yocto-bsp`)
- `openembedded-layer` (`meta-openembedded/meta-oe`, plus `meta-python`, `meta-networking`)
- `swupdate` (`meta-swupdate`)

## What's in here

| Path | Purpose |
|---|---|
| `conf/layer.conf` | Layer definition |
| `conf/templates/default/` | TEMPLATECONF: `local.conf` / `bblayers.conf` / notes |
| `setup-environment` | Sets `TEMPLATECONF` + runs `oe-init-build-env` (copied to workspace root by the manifest) |
| `wic/eicke-ab.wks.in` | A/B GPT layout: `esp` + `rootfs_a` + `rootfs_b` + `data` |
| `wic/grub.cfg` | GRUB-EFI A/B selection via `grubenv` (`rootdev`) + bootcount rollback |
| `recipes-core/images/eicke-image.bb` | Bootable image (core-image-minimal + swupdate + kernel-in-rootfs) |
| `recipes-support/swupdate/` | SWUpdate `defconfig` (GRUB handler) + bbappend |
| `recipes-extended/images/eicke-update-image.bb` | Produces the `.swu` update bundle |

## Build

See the `eicke-manifest` README. In short, inside the build container:

```sh
repo init -u ssh://git@github.com/KorribanMaster/eicke-manifest -b main -m default.xml
repo sync
. ./setup-environment
bitbake eicke-image          # -> tmp/deploy/images/<machine>/eicke-image-*.wic
bitbake eicke-update-image   # -> eicke-update-image-<machine>.swu
```

## A/B update flow

1. Device boots slot A (`rootdev` unset/`rootfs_a`); kernel + rootfs come from
   the `rootfs_a` partition.
2. `swupdate -i eicke-update-image-<machine>.swu -e stable,copy1` writes the new
   rootfs to `rootfs_b` and sets `rootdev=rootfs_b`, `ustate=1` in grubenv.
3. Reboot: GRUB boots slot B. After validating, userspace confirms the update
   (`grub-editenv /boot/EFI/BOOT/grubenv set ustate=0`). If it never confirms,
   the bootcount/`ustate` logic in `grub.cfg` rolls back to A.

> **Integration notes (verify when first building):** the mechanism that injects
> `grub.cfg`/seeds `grubenv` on the ESP (the `configfile=` wic sourceparam), the
> exact SWUpdate GRUB `CONFIG_*` symbols, and the `.swu` artifact filename/sha256
> token are documented inline in the respective files and should be confirmed
> against the synced `poky` / `meta-swupdate` sources.
