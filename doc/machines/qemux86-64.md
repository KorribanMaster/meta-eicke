# qemux86-64

Emulated 64-bit x86 machine (the openembedded-core default) for booting and testing the image
without hardware. It boots the A/B `.wic` through UEFI (OVMF) + GRUB, exactly
like the real target.

## Build

`qemux86-64` is the default `MACHINE`, so just:

```sh
bitbake eicke-image          # the bootable .wic
bitbake ovmf                 # UEFI firmware needed by 'runqemu … ovmf'
```

## Run

`runqemu` is invoked **inside the build container**. For KVM acceleration and
networking, start the container with `EICKE_DOCKER_QEMU=1` (adds `/dev/kvm` and
host networking):

```sh
EICKE_DOCKER_QEMU=1 .repo/manifests/dock.sh

# --- inside the container ---
source integration-init-build-env
runqemu eicke-image wic ovmf nographic kvm slirp
```

- `wic` boots the full A/B disk image, `ovmf` provides UEFI, `nographic` puts
  the serial console on your terminal, `slirp` is user-mode networking (no
  `tap`/root needed), `kvm` enables acceleration.

## Interact

- **Login:** at the `qemux86-64 login:` prompt log in as `root` (empty password;
  the template's `EXTRA_IMAGE_FEATURES` allow empty-password root login).
- **Which slot am I on?** `cat /proc/cmdline` → `root=PARTLABEL=rootfs_a` (or `_b`).
- **Boot state:** `grub-editenv /boot/EFI/BOOT/grubenv list`.
- **Quit QEMU:** `poweroff` inside the guest, or press `Ctrl-a x` to kill it.

## Apply an update under QEMU

Attach the `.swu` as a raw virtio disk (it shows up as `/dev/vda`, the only
virtio disk), then apply and reboot:

```sh
SWU=tmp/deploy/images/qemux86-64/eicke-update-image-qemux86-64.rootfs.swu
runqemu eicke-image wic ovmf nographic kvm slirp \
        qemuparams="-drive file=$PWD/$SWU,if=virtio,format=raw"

# in the guest (booted on slot A -> the hook auto-selects standby slot B):
swupdate -i /dev/vda
reboot
# after reboot: cat /proc/cmdline  ->  root=PARTLABEL=rootfs_b
```

The `eicke-bootconfirm` service then accepts slot B (`ustate=0`), so subsequent
reboots stay on B. If the new slot fails to boot, GRUB rolls back to A
automatically.

## Notes

- Without `/dev/kvm`, drop `kvm` from the command line; QEMU falls back to (much
  slower) software emulation.
- An unattended driver for the full boot → update → reboot → verify cycle lives
  in the build workspace as `qdrive.py` + `ab_test.json`.
