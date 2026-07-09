# meta-eicke

A small Yocto layer (**wrynose / 6.0 LTS**) that builds a bootable **x86-64**
image with a custom systemd-based **`eicke` distro**, a **GRUB-EFI** bootloader,
an **A/B (dual-copy) WIC disk image**, and **SWUpdate** for atomic OTA-style
updates. The whole build runs inside a **Docker container** (`crops/poky`), so
it works on hosts Yocto does not officially support (e.g. Arch Linux) — you only
need Docker + git + `repo`.

It is assembled by the [`eicke-manifest`](https://github.com/KorribanMaster/eicke-manifest)
`repo` manifest. As of 6.0 the Yocto Project no longer ships the combined poky
repo for new releases, so the manifest pulls in the individual upstream layers —
**bitbake**, **openembedded-core**, **meta-yocto** (for `meta-yocto-bsp`),
**meta-openembedded**, **meta-swupdate** and **meta-secure-core** (UEFI Secure
Boot + TPM2, used by the prod image) — and this layer provides the custom
`eicke` distro (`conf/distro/eicke.conf`) instead of the poky reference distro.

## What you get

- A custom **systemd**-based `eicke` distro (no poky reference distro).
- **GRUB-EFI** boot with an A/B slot layout: `esp` + `rootfs_a` + `rootfs_b` + `data`.
- **Per-slot kernel** — the kernel is loaded from the active slot, so a rootfs
  update updates the kernel too.
- **SWUpdate** `.swu` bundles that install to the standby slot, flip the GRUB
  environment, and roll back automatically if the new slot fails to confirm.
- A **boot-confirm** systemd service that accepts a slot once userspace is healthy.

## Get the sources (repo)

You need Google's [`repo`](https://gerrit.googlesource.com/git-repo/) tool and
an SSH key with access to the (private) repos.

```sh
mkdir -p ~/yocto/workspace && cd ~/yocto/workspace
mkdir -p ~/yocto/sstate-cache/
mkdir -p ~/yocto/downloads/
mkdir -p ~/yocto/keys/
repo init -u ssh://git@github.com/KorribanMaster/eicke-manifest -b main -m default.xml
repo sync
```

This populates the workspace with the layers under `sources/` (`bitbake/`,
`openembedded-core/`, `meta-yocto/`, `meta-openembedded/`, `meta-swupdate/`,
`meta-secure-core/`, `meta-eicke/`) and links the build helper
`integration-init-build-env` at the workspace root.

## Build

The build runs in the container; the host only needs Docker.

```sh
.repo/manifests/dock.sh            # build + enter the build container (cwd bind-mounted)

# --- inside the container ---
source integration-init-build-env  # sets TEMPLATECONF + runs oe-init-build-env, cd's into build-integration/
bitbake eicke-image                # -> tmp/deploy/images/<machine>/eicke-image-*.wic
bitbake eicke-update-image         # -> the *.swu update bundle
```

(Source the script by its bare name as shown — its template detection matches
on the script name.)

For development/bring-up there's a debug variant that adds on-target tools
(`gdb`, `lspci`/`lsusb`, `minicom`, `systemd-analyze`, `strace`, `tcpdump`, full
`util-linux`, …) on top of the same A/B base — see
[`doc/images/eicke-image-dev.md`](doc/images/eicke-image-dev.md):

```sh
bitbake eicke-image-dev            # debug image (.wic)
bitbake eicke-update-image-dev     # its *.swu update bundle
```

For production there's a hardened variant (real credentials — auto-generated on
first init, see the doc —, SSH key auth, UEFI Secure Boot, read-only rootfs) —
see [`doc/images/eicke-image-prod.md`](doc/images/eicke-image-prod.md):

```sh
bitbake eicke-image-prod           # hardened image (.wic)
bitbake eicke-update-image-prod    # its signed *.swu update bundle
```

Default machine is `qemux86-64`. Switch to real hardware by setting
`MACHINE = "genericx86-64"` in `build-integration/conf/local.conf`.

## Documentation

Start at [`doc/general.md`](doc/general.md) (index). Highlights:

- Images — [`doc/images/`](doc/images/): [eicke-image](doc/images/eicke-image.md),
  [eicke-update-image](doc/images/eicke-update-image.md),
  [eicke-image-dev](doc/images/eicke-image-dev.md) (debug tools),
  [eicke-image-prod](doc/images/eicke-image-prod.md) (hardened production image)
- Machines — [`doc/machines/`](doc/machines/):
  [qemux86-64](doc/machines/qemux86-64.md) (incl. how to run & interact),
  [genericx86-64](doc/machines/genericx86-64.md)
- Security — [Secure Boot setup](doc/secure-boot-setup.md),
  [TPM-sealed /data encryption](doc/encrypted-data-todo.md) (WIP)
