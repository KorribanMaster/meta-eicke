# eicke-image-sdr

[eicke-image](eicke-image.md) for the [Fish Ball SDR](../machines/fishball-sdr.md),
with the SDR stack its vendor firmware (an ADALM-Pluto port) offers: the same
A/B + SWUpdate base, ssh and networkd, plus the AD936x on IIO, served over the
network and USB.

- Recipe: `recipes-core/images/eicke-image-sdr.bb` (`require`s `eicke-image.bb`),
  `COMPATIBLE_MACHINE = "fishball-sdr"`
- Build: `MACHINE=fishball-sdr bitbake eicke-image-sdr`
- Update bundle: `bitbake eicke-update-image-sdr` → `eicke-update-image-sdr-fishball-sdr.rootfs.swu`
- Artifacts: `tmp/deploy/images/fishball-sdr/eicke-image-sdr-fishball-sdr.rootfs.wic`

## Extra packages (vs eicke-image)

| Package | What |
|---|---|
| `libiio-iiod` | IIO daemon: TCP 30431 and USB (FunctionFS), options in `/etc/default/iiod` |
| `libiio-tests` | `iio_info`, `iio_attr`, `iio_readdev`, `iio_writedev`, ... |
| `avahi-daemon` | iiod announces itself over mDNS |
| `fishball-sdr-gadget` | Pluto USB gadget: RNDIS/ECM/NCM at 192.168.2.1 with a DHCP server for the host, ACM console, iiod FunctionFS |
| `fishball-sdr-mac` | first boot: a permanent MAC in `uboot.env` |
| `ad936x-config` | `xo_correction` and the DMA block size from `uboot.env` |

The PL bitstream, the device tree and the watchdog feeder are not in this
list: they come with the machine, as every image needs them to boot there.

The AD936x variant (`ad936x_model`, `ad936x_mode`) and the other settings are
described in [fishball-sdr.md](../machines/fishball-sdr.md#ad936x-settings).

## Update

The bundle carries the rootfs, and with it the slot's `/boot`: kernel, device
tree and PL bitstream are updated, and rolled back, together.

```sh
swupdate -i eicke-update-image-sdr-fishball-sdr.rootfs.swu && reboot
# or:  curl -F filename=@eicke-update-image-sdr-fishball-sdr.rootfs.swu http://<board>:8080/upload
```

See [eicke-update-image](eicke-update-image.md) for the A/B mechanics.
