# ccimx8mn-dvk

**Digi ConnectCore 8M Nano Development Kit**: an i.MX 8M Nano SOM (4× Cortex-A53
+ a Cortex-M7) on Digi's DVK carrier. The board runs the eicke A/B image from a
**microSD card**, with the M7 driven by Linux `remoteproc` and the
[RustAmp](https://github.com/KorribanMaster/rustamp) rpmsg echo firmware on it.
For the emulated counterpart see [qemuimx8mp-evk](qemuimx8mp-evk.md).

## Where the BSP comes from

Digi's own layer, [meta-digi](https://github.com/digi-embedded/meta-digi), stops at
**scarthgap**, and mainline Linux/U-Boot have no ccimx8mn support. The machine is
therefore ported into meta-eicke:

| Piece | Source |
|---|---|
| Machine conf | `conf/machine/ccimx8mn-dvk.conf`, from meta-digi's `ccimx8mn-dvk.conf` + `ccimx8m.inc`, without DEY/TrustFence/wifi/bt/XBee |
| U-Boot (LPDDR4 timing, MCA, HWID) | `u-boot-dey` = [digi-embedded/u-boot](https://github.com/digi-embedded/u-boot) `v2024.04/maint` |
| Kernel (DTs `digi/ccimx8mn-dvk*`, MCA drivers) | `linux-dey` = [digi-embedded/linux](https://github.com/digi-embedded/linux) `v6.6/nxp/dey-5.0/maint` (6.6.52), `ccimx8_defconfig` + the eicke fragments |
| Boot container (SPL + DDR firmware + ATF + U-Boot) | meta-freescale (wrynose): `imx-boot`, `imx-atf`, `firmware-imx` |
| M7 firmware | `rustamp-m7-echo`, TCM build |

Both Digi recipes are in `dynamic-layers/freescale-layer/`. They are only parsed when
meta-freescale is in `bblayers.conf`, so the other machines don't depend on it.

### NXP EULA

`firmware-imx` (the LPDDR4 training blobs in the boot container) is under the NXP
EULA. Read `sources/meta-freescale/EULA`, then accept it in
`build-integration/conf/local.conf`:

```
ACCEPT_FSL_EULA = "1"
```

The layer deliberately doesn't set this for you.

## A/B boot

This board uses the same contract as qemuarm-uboot:
- The slot logic (canonicalize `rootdev`, `ustate`/`bootcount` rollback, slot →
  p2/p3) is **compiled into U-Boot** as its `bootcmd`
  (`u-boot-dey/files/eicke-bootcmd-ccimx8mn.cfg`). It replaces Digi's `boot.scr`.
- The A/B state lives in **`uboot.env` on the microSD's FAT partition**: env-in-FAT on
  the boot device, replacing Digi's eMMC boot1 environment. Userspace reaches it via
  `fw_printenv`/`fw_setenv` and SWUpdate's U-Boot handler (the `eicke-ubootenv`
  override).
- An **env whitelist** (`CONFIG_ENV_WRITEABLE_LIST` + `CFG_ENV_FLAGS_LIST_STATIC`)
  stops `uboot.env` from overriding anything except the A/B variables. The
  variables U-Boot and Digi's board code set at boot are also allowed, but the
  board code rewrites them after the import.
- Digi's own dual-boot (`active_system`, RTC bootcount, `altbootcmd`) is disabled.

The bootcmd loads the **active slot's** `/boot/Image` and `/boot/ccimx8mn-dvk.dtb`.
That makes the kernel and DT per-slot, like on the other machines. It then applies
these overlays from the same `/boot`, chosen as Digi's `boot.txt` would:

- `ccimx8mn_v1.dtbo` on SOM hardware version 1;
- `ccimx8mn-dvk_v1-v2.dtbo` on DVK board versions < 3;
- `eicke-ccimx8mn-cm7.dtbo` always.

The wifi/bt overlays are not applied: their Qualcomm firmware isn't shipped, so the
radios stay disabled.

The kernel gets `root=PARTUUID=<uuid of the slot partition on the microSD>` and
`eicke.slot=<slot>`, never `root=PARTLABEL=`. The DVK's **eMMC can hold its own A/B
install with the same GPT names** (`rootfs_a`, `rootfs_b`, `data`). Linux probes the
eMMC first, so a lookup by name boots the eMMC's rootfs, and an update could write
to it. Userspace uses `/dev/eicke/<name>` (see
[eicke-update-image](../images/eicke-update-image.md#how-it-updates-dual-copy)).

## Build

```sh
MACHINE=ccimx8mn-dvk bitbake eicke-image-dev         # microSD wic (+ .wic.bmap)
MACHINE=ccimx8mn-dvk bitbake eicke-update-image-dev  # its .swu
```

Card layout (`files/wic/eicke-ab-imx8m-sd.wks.in`):

| Where | What |
|---|---|
| 32 KiB, raw | `imx-boot` |
| p1 `esp` (FAT, from 8 MiB) | `uboot.env` |
| p2 `rootfs_a`, p3 `rootfs_b` | 1 GiB each |
| p4 `data` | 4 GiB |

## Flash and boot

```sh
DEPLOY=build-integration/tmp/deploy/images/ccimx8mn-dvk
bmaptool copy $DEPLOY/eicke-image-dev-ccimx8mn-dvk.rootfs.wic $DEV   # e.g. /dev/sdX on the host
```

1. Put the card in the DVK's microSD slot.
2. Set the boot-mode switches to **microSD**; see Digi's DVK hardware docs for the
   switch positions.
3. Attach the **console** on the DVK's debug USB (UART1 = `ttymxc0`, 115200 8N1).

U-Boot should print `Loading Environment from FAT... OK`, the overlays it applies,
and `Eicke Linux (rootfs_a): booting /boot/Image`. Then, on the target:

```sh
cat /proc/cmdline                     # eicke.slot=rootfs_a
fw_printenv rootdev ustate bootcount  # rootfs_a 0 0
```

Log in as `root` with an empty password (the dev image's `EXTRA_IMAGE_FEATURES`).

## Test the M7

```sh
ptest-runner rustamp-m7-echo
```

It starts the firmware through `/sys/class/remoteproc/remoteproc0`, waits for
`/dev/ttyRPMSG0`, and checks 2000 echo round trips. The manual steps are as in
[qemuimx8mp-evk](qemuimx8mp-evk.md#test-the-m7).

Verified on a ccimx8mn DVK (1 GiB SOM): `PASS: 2000 round trips`.

`eicke-ccimx8mn-cm7.dtbo` replaces Digi's `ccimx8mn_cm7.dtbo`, whose vrings and
resource table sit at 0xb8000000, past the end of RAM on the 512 MiB and 1 GiB SOMs.
Each part of ours fixes something that failed on the board:

- **Shared memory at 0x55000000:** vrings at 0x55000000 and 0x55008000, the rpmsg
  buffer pool at 0x55400000, and an `rsc-table` region at 0x550ff000.
- **The resource table is in DDR, not DTCM.** While the M7 runs, the A53 cannot
  access the TCMs: reads return stale data and writes are lost. With the table in
  DTCM, Linux's `DRIVER_OK` write never reaches the firmware, and no
  `/dev/ttyRPMSG0` appears. QEMU doesn't model this. The `rustamp-m7-echo` recipe
  patches the firmware's TCM linker script to place `.resource_table` at 0x550ff000.
  The `rsc-table` region must be at exactly that address: Linux loads the table
  there and updates it in place.
- **`clocks = <&clk IMX8MN_CLK_M7_CORE>`.** Digi's node claims no clock, so Linux
  gates `arm_m7_core` as unused late in boot. Loading the firmware into the TCMs then
  stalls the bus, and the whole SoC freezes right after `Booting fw image`.
- **`uart4` disabled for Linux.** Digi's DTS gives UART4 (the XBee 2 socket) to the
  A53, but meta-freescale's unpatched TF-A grants UART4 to the M7 domain only (RDC).
  Digi's TF-A patches that out. Without the `uart4` change, the kernel takes a
  synchronous external abort in `imx_uart_probe` and panics.

## Apply an update

Copy the `.swu` to the target (USB stick or `scp` to `/data`), then:

```sh
swupdate -i /data/eicke-update-image-dev-ccimx8mn-dvk.rootfs.swu
fw_printenv rootdev ustate     # rootfs_b 1
reboot                         # boots rootfs_b; eicke-bootconfirm then sets ustate=0
```

To exercise the rollback, arm a failed trial and reboot:

```sh
fw_setenv ustate 1 && fw_setenv bootcount 1
reboot
```

U-Boot then switches slots, and `fw_printenv ustate` shows `3`.

## Not covered (yet)

- **eMMC install.** The SOM's eMMC still holds Digi's image/env. Booting from it
  would need imx-boot in eMMC boot0 and this wic in the eMMC user area.
- **Production image and verified boot.** `eicke-image-prod` is not enabled for
  this machine, and there is no HAB/TrustFence signing.
- **Wireless, Bluetooth, XBee, MCA tooling (`mca-tool`).**
