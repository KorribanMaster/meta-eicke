# genericx86-64

Real 64-bit x86 PC hardware (the generic BSP shipped in `meta-yocto-bsp`,
checked out via the `meta-yocto` repo). Use this to run the image on a physical
machine.

## Build

Set the machine in `build/conf/local.conf`:

```
MACHINE = "genericx86-64"
```

then:

```sh
bitbake eicke-image
```

Artifact: `tmp/deploy/images/genericx86-64/eicke-image-genericx86-64.rootfs.wic`
(+ a `.wic.bmap`).

## Flash

Write the `.wic` to the target disk/USB (this erases the device):

```sh
bmaptool copy eicke-image-genericx86-64.rootfs.wic /dev/sdX
# or, without bmap:
sudo dd if=eicke-image-genericx86-64.rootfs.wic of=/dev/sdX bs=4M conv=fsync status=progress
```

Boot the target in **UEFI** mode. GRUB shows the `Eicke Linux (rootfs_a)` entry
and boots slot A.

## Update

Same flow as QEMU: copy the matching
`eicke-update-image-genericx86-64.rootfs.swu` onto the device (USB, scp, or the
`swupdate-www` web UI on port 8080) and apply it with `swupdate -i …` (or upload
via the web UI) — the standby slot is chosen automatically, no `-e` needed. See
[eicke-update-image](../images/eicke-update-image.md).

## Notes

- Requires a UEFI firmware (the image is GRUB-EFI only; no legacy BIOS).
- Console is on `ttyS0` (serial) and `tty0` (VGA) — see `wic/grub.cfg`.
