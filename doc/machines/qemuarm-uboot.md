# qemuarm-uboot

Emulated 32-bit ARM machine (QEMU `virt`, Cortex-A15) that boots the same A/B
image via **U-Boot** instead of GRUB-EFI. It exists to exercise the A/B +
SWUpdate scheme on an ARM/U-Boot boot chain; the disk layout, partition labels
and update flow are identical to the x86 machines.

How it maps to the GRUB scheme:

| | x86 (GRUB-EFI) | qemuarm-uboot (U-Boot) |
|---|---|---|
| Boot partition (p1, `esp`) | ESP: GRUB + grub.cfg + grubenv | FAT: `boot.scr` + `uboot.env` |
| A/B state (`rootdev`/`ustate`/`bootcount`) | grubenv | `uboot.env` (env-in-FAT) |
| Slot selection + rollback | `grub.cfg` | `boot.cmd` → `boot.scr` |
| Env tool on target | `grub-editenv` | `fw_printenv`/`fw_setenv` (libubootenv) |
| Kernel | `/boot/bzImage` in the active slot | `/boot/zImage` in the active slot |

## Build

Set the machine in `build-integration/conf/local.conf` (`MACHINE = "qemuarm-uboot"`)
or per invocation:

```sh
MACHINE=qemuarm-uboot bitbake eicke-image          # the bootable .wic
MACHINE=qemuarm-uboot bitbake eicke-update-image   # the *.swu update bundle
```

No firmware recipe is needed (`u-boot` is built implicitly; QEMU loads
`u-boot.bin` directly as its "BIOS" — no OVMF equivalent).

## Run

`runqemu` is invoked **inside the build container**. The machine conf sets
`QB_DEFAULT_BIOS = "u-boot.bin"`, so U-Boot is picked up automatically:

```sh
.repo/manifests/dock.sh

# --- inside the container ---
source integration-init-build-env
MACHINE=qemuarm-uboot runqemu eicke-image wic nographic slirp
```

- No `kvm`: an ARM32 guest cannot use KVM on an x86 host (TCG emulation only).
- U-Boot loads its environment from `uboot.env` on the boot partition
  ("Loading Environment from FAT... OK"), runs `boot.scr`, loads the active
  slot's `/boot/zImage` and boots with the QEMU-generated device tree.

## Interact

- **Login:** as `root` (empty password; the template's `EXTRA_IMAGE_FEATURES`
  allow empty-password root login).
- **Which slot am I on?** `cat /proc/cmdline` → `root=PARTLABEL=rootfs_a` (or `_b`).
- **Boot state:** `fw_printenv rootdev ustate bootcount` (reads
  `/boot/uboot.env` via `/etc/fw_env.config`).
- **Quit QEMU:** `poweroff` in the guest, or `Ctrl-a x`.

## Apply an update under QEMU

Attach the `.swu` as a second virtio disk — the wic disk is `/dev/vda`, so the
bundle shows up as `/dev/vdb`:

```sh
SWU=tmp/deploy/images/qemuarm-uboot/eicke-update-image-qemuarm-uboot.rootfs.swu
MACHINE=qemuarm-uboot runqemu eicke-image wic nographic slirp \
        qemuparams="-drive file=$PWD/$SWU,if=virtio,format=raw"

# in the guest (booted on slot A -> the hook auto-selects standby slot B):
swupdate -i /dev/vdb
reboot
# after reboot: cat /proc/cmdline  ->  root=PARTLABEL=rootfs_b
```

The `eicke-bootconfirm` service then accepts slot B (`ustate=0`, via
`fw_setenv`), so subsequent reboots stay on B. If the new slot fails to boot,
`boot.scr` rolls back to A automatically (`ustate=3`).

## Notes

- runqemu opens the deployed `.wic` read-write, so guest env/rootfs writes
  dirty the build artifact. Append `snapshot` to the runqemu command to keep
  it pristine (changes persist across guest reboots within one QEMU run and
  are discarded on exit — exactly what an A/B update test needs).
- Rollback lives in `boot.scr` (mirroring `grub.cfg`), not in U-Boot's
  `CONFIG_BOOTCOUNT_LIMIT` machinery: a hang before the script runs does not
  increment the counter — the same limitation as the GRUB flow.
- The env is shared between U-Boot and userspace through the FAT file
  `/boot/uboot.env`; its size (0x10000) must match in three places: the u-boot
  Kconfig fragment (`CONFIG_ENV_SIZE`), `ubootenv.bb` (`mkenvimage -s`) and
  `/etc/fw_env.config`.
- `eicke-image-prod` builds for this machine too: real credentials, SSH key
  auth, read-only rootfs with the `/etc` overlay on `/data`, minimal kernel
  modules and signed update bundles all apply — but **without** the UEFI
  Secure Boot chain and TPM/LUKS wiring (both x86-gated in the recipe;
  U-Boot FIT verified boot would be the ARM equivalent and is not wired up).
  Log in via SSH (`ssh -p 2222 root@127.0.0.1` under runqemu slirp) — serial
  login needs the real root password from the credentials file. The netboot
  variants remain x86-only.
