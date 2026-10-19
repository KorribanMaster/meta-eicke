# qemuimx8mp-evk

Emulated **i.MX 8M Plus EVK** (QEMU `imx8mp-evk`: 4× Cortex-A53 + a Cortex-M7)
running the eicke A/B image, with the M7 driven by Linux `remoteproc`
(`imx_rproc`) and the [RustAmp](https://github.com/KorribanMaster/rustamp)
rpmsg echo firmware on it.

QEMU has no i.MX 8M **Nano** model. The 8M Plus has the same Cortex-M7 memory
map and Messaging Unit, so the RustAmp `imx8mn-m7-echo` firmware runs on it
unchanged (built with `--features qemu-imx8mp`).

## What makes it work

| Piece | Where |
|---|---|
| M7 core, GPC, GPR (`CPUWAIT`), SRC, MU, M7 clocks in QEMU | `recipes-devtools/qemu/` — NXP's v2 *Cortex-M7 AMP boot* series, backported to QEMU 10.2, applied to `qemu-system-native` |
| MU register-offset fix (Linux `imx-mailbox` kicks otherwise time out) | `recipes-devtools/qemu/files/imx8mp-m7-amp/0007-*` (from RustAmp) |
| Kernel: i.MX 8M Plus drivers, `imx_rproc`, `imx-mailbox`, `rpmsg_tty` | `recipes-kernel/linux/linux-yocto/imx8mp-qemu.cfg` (KMACHINE `qemuarm64`) |
| Device tree: EVK + M7 node, vrings, rpmsg buffer pool, M7 carve-out | `recipes-kernel/linux/linux-yocto/imx8mp-evk-rpmsg-qemu.dts` (from RustAmp) |
| M7 firmware → `/lib/firmware/rustamp-echo.elf` | `recipes-bsp/rustamp-m7-echo/` (cargo, prebuilt `rust-bin-thumbv7em-native` toolchain) |
| Echo test | `rustamp-m7-echo-ptest` (in `eicke-image-dev`) |

## A/B on a machine without a bootloader

QEMU `imx8mp-evk` cannot run U-Boot (it has no SPL/DDR-init model), so the
kernel is booted directly (`-kernel`/`-dtb`) by
`scripts/eicke-runqemu-imx8mp.sh`. **That script picks the slot** (`SLOT=rootfs_a`
by default) instead of a boot script.

The disk is still the A/B layout from `eicke-ab-uboot.wks.in`. The A/B state
(`rootdev`/`ustate`/`bootcount`) is still kept in a U-Boot-format `uboot.env` on
the boot partition (the `eicke-ubootenv` override, shared with
qemuarm-uboot). So SWUpdate writes the standby slot and flips `rootdev`, and
`eicke-bootconfirm` clears `ustate`, exactly as on the other machines. What is
missing is the boot-time half:

- the kernel always comes from the deploy dir, not from the slot's `/boot`;
- nothing reads `rootdev` at boot: you choose the slot with `SLOT=`;
- there is no automatic rollback.

## Build

```sh
MACHINE=qemuimx8mp-evk bitbake eicke-image-dev        # A/B wic with the M7 firmware + ptest
MACHINE=qemuimx8mp-evk bitbake eicke-update-image-dev # its .swu
```

The first build rebuilds `qemu-system-native` with the series (for every
machine, since the native recipe is shared). The firmware recipe fetches
RustAmp anonymously over HTTPS (public repo).

## Run

Inside the build container, from `build-integration/` (after
`source integration-init-build-env`):

```sh
IMAGE=eicke-image-dev ../sources/meta-eicke/scripts/eicke-runqemu-imx8mp.sh
```

- The A53 console (UART2, `ttymxc1`) is on your terminal; `Ctrl-a x` quits.
- The M7's UART4 goes to `./m7-uart4.log` (`M7LOG=`).
- SSH: `ssh -p 2222 root@127.0.0.1` (slirp, `SSH=` to change the port).
- No KVM: aarch64 on an x86 host runs under TCG.
- The deployed `.wic` is never written. The guest gets a qcow2 overlay, sized
  up to a power of two because QEMU's SD card requires it. The overlay is
  deleted on exit unless `KEEP=1`.

## Test the M7

```sh
ptest-runner rustamp-m7-echo
# [rpmsg-echo] started /sys/class/remoteproc/remoteproc0 with rustamp-echo.elf
# [rpmsg-echo] /dev/ttyRPMSG0 is up after ... ms
# [rpmsg-echo] PASS: 2000 round trips, ... bytes echoed by the RustAmp firmware
```

`m7-uart4.log` shows the firmware's side (`announced rpmsg-tty`,
`echoed 2000 messages`). By hand:

```sh
echo rustamp-echo.elf > /sys/class/remoteproc/remoteproc0/firmware
echo start > /sys/class/remoteproc/remoteproc0/state
stty -F /dev/ttyRPMSG0 raw -echo
cat /dev/ttyRPMSG0 & echo hello > /dev/ttyRPMSG0
```

Start the M7 **once per boot**: the v2 series does not reset the M7 on a second
`start`, so `stop` + `start` does not reboot the firmware.

## Apply an update

Attach the `.swu` as a second SD card and keep the disk overlay across runs:

```sh
SWU=tmp/deploy/images/qemuimx8mp-evk/eicke-update-image-dev-qemuimx8mp-evk.rootfs.swu
KEEP=1 OVERLAY=/tmp/imx8mp-ab.qcow2 IMAGE=eicke-image-dev \
  QEMU_EXTRA="-drive file=$PWD/$SWU,if=sd,bus=1,format=raw,readonly=on" \
  ../sources/meta-eicke/scripts/eicke-runqemu-imx8mp.sh
# guest: swupdate -i /dev/mmcblkX   (the second SD card)
#        fw_printenv rootdev ustate  -> rootfs_b, 1
#        poweroff

KEEP=1 OVERLAY=/tmp/imx8mp-ab.qcow2 SLOT=rootfs_b IMAGE=eicke-image-dev \
  ../sources/meta-eicke/scripts/eicke-runqemu-imx8mp.sh
# guest: cat /proc/cmdline -> eicke.slot=rootfs_b; fw_printenv ustate -> 0
```
