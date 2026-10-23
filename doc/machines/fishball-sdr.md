# fishball-sdr

The **Fish Ball SDR** ("7020_AD936X_SDR"): a Xilinx Zynq-7000 XC7Z020-2CLG400
(2× Cortex-A9), 1 GiB DDR3L and an Analog Devices AD936x transceiver (2R2T,
LVDS, 40 MHz reference) in a small case with two USB-C ports. It is built like an
ADALM-Pluto, and its vendor ships a port of ADI's Pluto firmware. The board
runs the eicke A/B image from a **microSD card**, with both A9 cores in Linux
(SMP) and the AD936x on IIO, served by iiod over Ethernet and USB.

The image for it is [eicke-image-sdr](../images/eicke-image-sdr.md). Other
eicke images build for the machine too and boot, but without the SDR stack.

## Where the BSP comes from

No meta-xilinx and no Xilinx tools in the build.

| Piece | Source |
|---|---|
| Machine conf | `conf/machine/fishball-sdr.conf` |
| Boot ROM payload (replaces the Xilinx FSBL) | U-Boot SPL `boot.bin`, `xilinx_zynq_virt_defconfig` + `recipes-bsp/u-boot/files/fishball-sdr/` |
| DDR, clocks, MIO | `ps7_init_gpl.c/h` from the vendor's Vivado handoff, built into SPL |
| U-Boot | oe-core `u-boot` 2026.01, `u-boot.img` (FIT) |
| PL bitstream | `fishball-sdr-bitstream`, the vendor's prebuilt bitstream (`/boot/fishball-sdr.bit` in each slot) |
| Kernel | `linux-adi` 6.12: ADI's fork, release `2026_R1`, `zynq_xcomm_adv7511_defconfig` + `hardening.cfg`, `overlayfs.cfg`, `fishball-sdr.cfg` |
| Device trees | `files/dts/zynq-fishball-sdr.dts` (PS, U-Boot and Linux), `files/dts/zynq-fishball-sdr-ad936x.dts` (Linux: PL cores + AD936x) |
| SDR userspace | `libiio` (meta-oe: iiod, tools), `fishball-sdr-services` |

**Why ADI's kernel.** Mainline Linux, and linux-yocto 6.18 with it, has no
driver for the AD9361/AD9363/AD9364 or for ADI's `cf_axi_adc`/`cf_axi_dds`
converter cores. ADI's fork has them and is what the vendor's firmware runs
(5.15 there). `linux-adi` fetches a shallow clone of `2026_R1` and the
kernel-cache metadata (for oe-core's `cfg/*.scc` features only). It is
configured from ADI's defconfig plus the `.cfg` fragments, exactly as
linux-yocto merges them. A `do_eicke_check_fragment` task fails the build when
a `=y` of `fishball-sdr.cfg` does not make it into `.config`.

### The vendor package

The vendor package ("7020_AD936X_SDR资料") holds two Linux ports, Vivado
projects, the schematic (`硬件资料/7020_936x_SDR.pdf`) and Windows tools. There
is no PetaLinux or buildroot project source, only build outputs:

- **Pluto firmware port** (`Pluto固件相关/基于pluto-fw-v0.38移植固件（支持网口和USB）`).
  It is what the board ships with on its card: pluto-fw v0.38 with a 5.15
  kernel, Ethernet and USB. The source is a fork on GitHub
  (`Xiaozhang-code-cloud/Fish-Wan-plutosdr-fw-7020-SDR`).
- **ZC706-FMCOMMS2/3 port** (`Pluto固件相关/基于ZC706-FMCOMMS2-3移植固件(支持网口）`).
  It holds the PetaLinux 2022.2 outputs (`固件.zip`: `BOOT.BIN`, `image.ub`,
  rootfs), a card image, and the HDL (`HDL-Vivado2021.1/7020_SDR_ZC706_My.zip`):
  ADI's hdl repository with `projects/fmcomms2` retargeted to this board.

This machine uses the **ZC706-FMCOMMS2/3 port's hardware**. The top-level
`system_top.xsa` in `7020_SDR_ZC706_My.zip` is the handoff that port's
PetaLinux build was made from, which makes it the one known-working set of PS
configuration and bitstream:

- Its `ps7_init_gpl.c` matches the FSBL in the PetaLinux `BOOT.BIN` register
  for register (201 of 201 writes).
- Its `system_top.bit` is, byte for byte, the bitstream in that `BOOT.BIN`.

The other handoffs in the package differ, so don't swap them in without a
reason:

- **`V1.xsa`, in the same zip.** It sets bank 0 (MIO 0–15) to 1.8 V on a 3.3 V
  bank and drops pull-ups.
- **`Vivado2021.1工程/PL_PS驱动`.** It uses different DDR I/O settings (external
  VREF, drive strengths) and puts the AD936x control lines on other EMIO pins.

The DDR settings of the chosen handoff are a DDR3 profile (internal VREF) on
DDR3L parts; they are what the vendor's PetaLinux runs with.

The PL design is ADI's fmcomms2: `axi_ad9361` (ADC core at `0x79020000`, DDS
at `0x79024000`), `axi_dmac` for RX (`0x7c400000`, IRQ 57) and TX
(`0x7c420000`, IRQ 56), `util_cpack2`/`util_upack2`, and the AD936x on PS SPI0
over EMIO. The device tree is ADI's zc702-fmcomms2 layout
(`adi-fmcomms2.dtsi` from the ADI tree) with:

- the AD936x control lines on EMIO 44–48 (`en_agc`, `sync`, `reset`, `enable`,
  `txnrx` = gpio0 98–102), so the ENSM can also be pin-controlled;
- `adi,xo-disable-use-ext-refclk-enable`, because the 40 MHz reference is an
  oscillator (Y4) into XTALN, as in the vendor's Pluto port (their PetaLinux
  tree leaves the DCXO on);
- the FMCOMMS card's I2C devices (AD7291, EEPROM) hung off the disabled I2C0,
  since the board has neither.

`ps7_init_gpl.c/h` and the bitstream are copied from the xsa (a zip) unchanged.
After a change of the design in Vivado, export the xsa again and copy the
three files out of it.

### Hardware described

From the vendor's `system_top.xsa` and the schematic:

| MIO | Function | Device tree |
|---|---|---|
| 0 | White LED | `gpio-leds`, heartbeat |
| 1–6 | QSPI flash (W25Q256, 32 MiB) | not described yet |
| 8–9 | UART1, console (FT2232HL channel B on the "JTAG" USB-C) | `&uart1`, `ttyPS0` |
| 16–27, 52–53 | GEM0 RGMII + MDIO, RTL8211E | `&gem0`, PHY at address 1 |
| 28–39 | USB0 ULPI, USB3320C on the OTG USB-C | `&usb0`, `dr_mode = "otg"` |
| 40–45 | SD0, microSD (boot, A/B disk) | `&sdhci0`, `mmc0` |
| 46 | USB3320C reset (active low) | GPIO hog, as on ax7015b |

- The **RTL8211E** strap gives MDIO address 1 (it also answers on 0, which
  the vendor trees use). The RGMII delays are strapped in the PHY, and its
  reset is tied to the power-on reset.
- **SD0** runs at 1.8 V behind a TXS02612 level shifter: no UHS
  (`no-1-8-v`), and card detect and write protect are not wired.
- **The OTG port** switches 5 V out in host mode (USB3320C CPEN). As a device
  it carries the Pluto USB gadget.

## Boot flow and A/B

1. The boot ROM (DIP switch on SD) loads `boot.bin` (U-Boot SPL) from the
   first FAT partition of the microSD.
2. SPL runs `ps7_init` and loads `u-boot.img` from the same partition.
3. U-Boot starts the **system watchdog** (60 s) and runs the compiled-in eicke
   bootcmd (`recipes-bsp/u-boot/files/fishball-sdr/eicke-fishball-sdr.cfg`):
   - the A/B logic of [ax7015b](ax7015b.md#boot-flow-and-ab): `rootdev`,
     `ustate` and `bootcount` in `uboot.env` on the FAT partition, rollback of
     a trial boot that did not confirm;
   - it loads the **active slot's** `/boot/fishball-sdr.bit` into the PL
     (`fpga loadb`), then `/boot/zImage` and
     `/boot/zynq-fishball-sdr-ad936x.dtb`;
   - it patches the AD936x variant into the device tree (below) and boots with
     `root=PARTUUID=<slot partition>` and `eicke.slot=<slot>`.

The bitstream belongs to the slot like the kernel and the device tree. An
update brings the matching three together, and a rollback switches all three
back. A slot without a bitstream does not boot: the device tree describes the
PL cores, and touching them with the PL unconfigured hangs the bus. The
bitstream, the device tree and the watchdog feeder (`eicke-watchdog`) are
therefore in the machine conf (`MACHINE_ESSENTIAL_EXTRA_*`), not in an image.

The **watchdog** works as on te0820: U-Boot starts it and does not feed it,
and systemd feeds it from PID 1. A kernel or userspace that hangs, or a trial
slot stuck at the U-Boot prompt, is reset after 60 s into the rollback. At
the prompt, type `wdt stop` first.

The env whitelist (`0001-zynq-eicke-env-flags-whitelist-fishball-sdr.patch`)
is ax7015b's plus the SDR variables. Every variable userspace keeps in
`uboot.env` must be on it: U-Boot drops the others on its next `saveenv`, and
the A/B bootcmd saves on every trial boot.

## AD936x settings

As on a Pluto, the settings live in the U-Boot environment and are applied at
boot:

```sh
fw_setenv ad936x_model ad9363a   # ad9361 (default) | ad9363a | ad9364
fw_setenv ad936x_mode 1r1t       # 2r2t (default) | 1r1t; ad9364 is always 1r1t
fw_setenv xo_correction 40000123 # reference clock correction in Hz
fw_setenv iio_max_block_size 67108864
reboot
```

- `ad936x_model` and `ad936x_mode` change the device tree, so **U-Boot**
  applies them. The bootcmd canonicalizes them to the literals above (the env
  is untrusted) and sets the AD936x node's `compatible`. For 1r1t it removes
  `adi,2rx-2tx-mode-enable` and switches the DDS core to its one-channel
  compatible, like Pluto's `adi_loadvals`. The U-Boot banner shows the result:
  `Eicke Linux (rootfs_a): booting /boot/zImage (ad9361 2r2t)`.
- `xo_correction` and `iio_max_block_size` are applied in Linux by
  `ad936x-config.service`, like pluto-fw's `S21misc`.

Whether the chip on a given board is an AD9361 or a relabelled AD9363 is not
known. The vendor configures it as an AD9361 (`ad9361`, 2r2t), which is the
default.

## Pluto features

`eicke-image-sdr` brings what the vendor's Pluto port offers, ported to
systemd:

- **iiod** on TCP 30431, announced over Avahi. Remote libiio clients (IIO
  Oscilloscope, SDR#, GNU Radio, pyadi-iio, SDR++) use `ip:<board>`.
- **USB gadget** on the OTG port (`fishball-sdr-gadget`, after pluto-fw's
  `S23udc`). It uses Pluto's USB IDs (0456:b673), so the host's PlutoSDR
  drivers and libiio's USB discovery take it for one. It has:
  - **RNDIS** networking by default, or ECM/NCM with
    `fw_setenv usb_ethernet_mode ecm|ncm`. The board is `192.168.2.1`, and
    networkd's DHCP server gives the host an address from `192.168.2.10`.
    usb0 keeps its address with no host plugged in (`ConfigureWithoutCarrier`).
  - an **ACM console** with a login on `ttyGS0`. systemd does not reset that
    tty (drop-in `serial-getty@ttyGS0.service.d/10-fishball-sdr.conf`): a
    write to ttyGS0 blocks until a host opens it, and systemd's reset holds
    the `/dev/console` lock meanwhile, which left the `ttyPS0` console without
    a login prompt whenever the OTG port was unplugged.
  - **iiod over FunctionFS** (`iio_info -u usb:`).
- **A permanent MAC address.** The board has no MAC storage, so on its first
  boot `fishball-sdr-mac` writes a random locally administered `ethaddr` to
  `uboot.env`. From the next boot on, U-Boot uses it and passes it to Linux.
  (If a `saveenv` comes first, e.g. the trial boot after an update, U-Boot's
  own random MAC is stored instead; it is locally administered too, and
  `fishball-sdr-mac` keeps it.)
  It survives updates and slot switches (the DHCP lease does too), and it is
  the USB serial number.
- The libiio tools on the board: `iio_info`, `iio_attr`, `iio_readdev`,
  `iio_writedev`.

Left out: the Pluto's mass storage drive with `config.txt` and drop-in
firmware updates (SWUpdate does updates here), the JFFS2 settings partition,
and the Wi-Fi dongle support.

## Build

```sh
MACHINE=fishball-sdr bitbake eicke-image-sdr         # microSD wic (+ .wic.bmap)
MACHINE=fishball-sdr bitbake eicke-update-image-sdr  # its .swu
```

The card layout is ax7015b's (`files/wic/eicke-ab-zynq-sd.wks.in`, MBR): p1
`esp` (FAT, `boot.bin`, `u-boot.img`, `uboot.env`), p2/p3 `rootfs_a`/`rootfs_b`,
p4 `data`.

## Flash and boot

```sh
DEPLOY=build-integration/tmp/deploy/images/fishball-sdr
bmaptool copy $DEPLOY/eicke-image-sdr-fishball-sdr.rootfs.wic $DEV   # e.g. /dev/sdX on the host
```

1. Put the card in the board's microSD slot.
2. Set the two-position boot DIP switch to **SD**: both levers down ("00", the
   factory setting; MIO4/5 high). MIO4 low alone is QSPI, both low is JTAG.
   If unsure, read the straps over JTAG (the same USB-C, Vitis `xsdb`):
   `connect; targets -set -filter {name =~ "APU*"}; mrd -force 0xF800025C`
   reads `5` for SD, `0` for JTAG.
3. Plug the **"JTAG" USB-C** into the host. It powers the board, and channel B
   of its FT2232HL is the console (`UART1` = `ttyPS0`, 115200 8N1; the host's
   second `ttyUSB` of the pair).
4. Plug Ethernet in (DHCP), or the **OTG USB-C** for the USB network.

SPL prints `U-Boot SPL ...`, and U-Boot prints `Loading Environment from
FAT... OK` and `Eicke Linux (rootfs_a): booting /boot/zImage (ad9361 2r2t)`.
Log in as `root` with an empty password (the build's `EXTRA_IMAGE_FEATURES`).
Then, on the target:

```sh
cat /sys/class/fpga_manager/fpga0/state   # operating
iio_info | grep -E '^\s+iio:device'       # ad9361-phy, cf-ad9361-lpc, cf-ad9361-dds-core-lpc
fw_printenv rootdev ustate ethaddr
```

From the host: `iio_info -u ip:<board>`, or IIO Oscilloscope with the board's
address.

## Test on the board

```sh
pytest meta-eicke/scripts/test/test_fishball_sdr.py --board <board> --build
```

SSH only (see [testing](../testing.md#on-the-board)). It checks:

- the bitstream is loaded and the IIO devices are up;
- the MAC comes from `uboot.env`;
- an RX capture of 64k samples through the DMA returns data;
- iiod answers on TCP 30431;
- the USB gadget is bound;
- the 1r1t variant and back;
- an A/B update and a rollback;
- a hung kernel is reset by the watchdog.

All ten steps passed on the board on 2026-10-23 (eicke-image-sdr, both
slots). Not verified yet: the host side of the USB gadget (RNDIS/ECM network,
`ssh root@192.168.2.1`, `iio_info -u usb:`, the ACM console), which needs the
OTG port plugged into a host and is checked by hand.

## Not done yet

- **QSPI boot.** The W25Q256 is not described in the device tree yet (as on
  ax7015b, the mainline Zynq QSPI driver needs 4-byte addressing work for
  it). A QSPI SPL would make the card optional for booting.
- **Rebuilding the PL design.** The bitstream is the vendor's prebuilt one.
  Changing the design needs Vivado and the vendor's HDL (ADI's hdl, fmcomms2
  project in `7020_SDR_ZC706_My.zip`).
- **Prod image.** `eicke-image-prod` (signed kernel FIT, read-only rootfs) is
  not enabled for this machine. It would also have to sign the bitstream.
- **Pluto extras.** No mass storage and `config.txt`, Wi-Fi dongles, or
  calibration helpers (`ad936x_ref_cal`).
