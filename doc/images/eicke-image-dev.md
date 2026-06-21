# eicke-image-dev

A **development** build of [eicke-image](eicke-image.md): the exact same GRUB-EFI
A/B + SWUpdate base, plus a curated set of on-target debugging tools. Use it for
hardware bring-up; keep the lean `eicke-image` for production/OTA.

- Recipe: `recipes-core/images/eicke-image-dev.bb` (`require`s `eicke-image.bb`)
- Build: `bitbake eicke-image-dev`
- Update bundle: `bitbake eicke-update-image-dev` → `eicke-update-image-dev-<machine>.rootfs.swu`
- Artifacts: `tmp/deploy/images/<machine>/eicke-image-dev-<machine>.rootfs.wic`

## Extra tools (vs eicke-image)

| Area | Tools |
|---|---|
| Bus / hardware | `lsusb` (usbutils), `lspci` (pciutils), `i2c-tools`, `dmidecode`, `devmem2`, `evtest` |
| Serial | `minicom` |
| Debug / trace | `gdb` + `gdbserver`, `strace`, `ltrace` |
| Boot timing | `systemd-analyze` (`blame`, `critical-chain`) |
| Network | `tcpdump`, `ethtool`, `iproute2` (ip/ss), `curl` |
| Process / FS | `procps` (ps/top/free), `htop`, `lsof`, full `util-linux` (findmnt/lsblk/dmesg), `e2fsprogs`, `file` |
| Editors | `vim`, `less` |

Enabled image features: `tools-debug` (gdb/gdbserver/strace), `package-management`
(install more packages on target at runtime). Root login with empty password is
already on (global `EXTRA_IMAGE_FEATURES`), and SSH is from the base.

## Optional, not enabled by default
- Source-level symbols for gdb: add `IMAGE_FEATURES += "dbg-pkgs"` (large), or use
  the wrynose `debuginfod` feature to fetch symbols on demand.
- Deeper profiling: `IMAGE_FEATURES += "tools-profile"` (perf, valgrind, powertop, …).

## Update an A/B device with the dev image
Same flow as the base, using the dev bundle (from the running target, on slot A):
```sh
swupdate -i eicke-update-image-dev-<machine>.rootfs.swu -e stable,copy1
```
See [eicke-update-image](eicke-update-image.md) for the A/B mechanics.
