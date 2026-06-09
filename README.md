# meta-eicke

A small Yocto layer (poky **scarthgap / 5.0 LTS**) that builds a bootable
**x86-64** image with a **GRUB-EFI** bootloader, an **A/B (dual-copy) WIC disk
image**, and **SWUpdate** for atomic OTA-style updates. The whole build runs
inside a **Docker container** (`crops/poky`), so it works on hosts Yocto does
not officially support (e.g. Arch Linux) — you only need Docker + git + `repo`.

It is assembled by the [`eicke-manifest`](https://github.com/KorribanMaster/eicke-manifest)
`repo` manifest, which pulls in poky, meta-openembedded and meta-swupdate.

## What you get

- **GRUB-EFI** boot with an A/B slot layout: `esp` + `rootfs_a` + `rootfs_b` + `data`.
- **Per-slot kernel** — the kernel is loaded from the active slot, so a rootfs
  update updates the kernel too.
- **SWUpdate** `.swu` bundles that install to the standby slot, flip the GRUB
  environment, and roll back automatically if the new slot fails to confirm.
- A **boot-confirm** service that accepts a slot once userspace is healthy.

## Get the sources (repo)

You need Google's [`repo`](https://gerrit.googlesource.com/git-repo/) tool and
an SSH key with access to the (private) repos.

```sh
mkdir -p ~/eicke && cd ~/eicke
repo init -u ssh://git@github.com/KorribanMaster/eicke-manifest -b main -m default.xml
repo sync
```

This populates the workspace with `poky/`, `meta-openembedded/`,
`meta-swupdate/`, `meta-eicke/` and a `setup-environment` helper.

## Build

The build runs in the container; the host only needs Docker.

```sh
.repo/manifests/dock.sh            # build + enter the build container (cwd bind-mounted)

# --- inside the container ---
. ./setup-environment              # sets TEMPLATECONF + runs oe-init-build-env
bitbake eicke-image                # -> tmp/deploy/images/<machine>/eicke-image-*.wic
bitbake eicke-update-image         # -> the *.swu update bundle
```

Default machine is `qemux86-64`. Switch to real hardware by setting
`MACHINE = "genericx86-64"` in `build/conf/local.conf`.

## Documentation

- Images — [`doc/images/`](doc/images/): [eicke-image](doc/images/eicke-image.md),
  [eicke-update-image](doc/images/eicke-update-image.md)
- Machines — [`doc/machines/`](doc/machines/):
  [qemux86-64](doc/machines/qemux86-64.md) (incl. how to run & interact),
  [genericx86-64](doc/machines/genericx86-64.md)
