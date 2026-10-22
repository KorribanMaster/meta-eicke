SUMMARY = "Eicke SDR image: eicke-image + the Pluto-style SDR stack (fishball-sdr)"
DESCRIPTION = "eicke-image (A/B boot, SWUpdate, ssh, networkd) for the Fish \
Ball SDR, with what its vendor firmware (an ADALM-Pluto port) offers: the \
AD936x on IIO, served by iiod over the network and over USB, the Pluto USB \
gadget (network at 192.168.2.1, ACM console), the libiio command line tools, \
and the AD936x settings in uboot.env."
LICENSE = "MIT"

require recipes-core/images/eicke-image.bb

COMPATIBLE_MACHINE = "fishball-sdr"

# The bitstream, device tree and watchdog feeder come with the machine
# (fishball-sdr.conf), as every image needs them to boot.
#
# libiio-iiod: the IIO daemon (TCP 30431 + FunctionFS, options in
#   /etc/default/iiod from fishball-sdr-gadget);
# libiio-tests: iio_info, iio_attr, iio_readdev, iio_writedev, ...;
# avahi-daemon: iiod announces itself over mDNS, so IIO Oscilloscope and
#   libiio's "ip:" discovery find the board without its address.
IMAGE_INSTALL:append = " \
    libiio-iiod \
    libiio-tests \
    avahi-daemon \
    fishball-sdr-gadget \
    fishball-sdr-mac \
    ad936x-config \
"
