"""fishball-sdr on the bench: the SDR stack, the AD936x variant switch, A/B
updates and the watchdog, over SSH.

Runs against a real board running eicke-image-sdr (pass --board HOST). The
update bundle (eicke-update-image-sdr, --build builds it) is uploaded to
SWUpdate's web server (:8080), everything else goes over SSH as root. The
steps:

  1. the board booted its slot with the bitstream loaded and the AD936x up;
  2. its MAC comes from uboot.env (fishball-sdr-mac);
  3. an RX capture through the AXI DMA returns samples;
  4. iiod answers on TCP 30431 from the host;
  5. the USB gadget is bound, with iiod's FunctionFS function;
  6. ad936x_mode=1r1t (U-Boot patches the device tree) gives one RX and one
     TX channel pair, and back;
  7. an A/B update to the other slot, and a rollback of an unconfirmed trial
     boot;
  8. a hung kernel is reset by the watchdog (U-Boot starts it, systemd feeds
     it).

It reboots six times. A step that leaves the board unbootable needs the
serial console or the microSD card (doc/machines/fishball-sdr.md). Not
covered: the host side of the USB gadget (the OTG port must be plugged into
the host: ssh root@192.168.2.1, iio_info -u usb:).
"""

import re
import socket

import pytest
from eicke_board import Board
from eicke_qemu import wait_for

MACHINE = "fishball-sdr"
PHY = "/proc/device-tree/axi/spi@e0006000/ad9361-phy@0"
DDS = "/proc/device-tree/fpga-axi@0/cf-ad9361-dds-core-lpc@79024000"
IIOD_PORT = 30431

pytestmark = pytest.mark.board


class Sdr(Board):
    def iio_dev(self, name):
        """The sysfs directory of the IIO device called name."""
        out = self.out(f"grep -lx '{name}' /sys/bus/iio/devices/iio:device*/name")
        return out.splitlines()[0].rsplit("/", 1)[0]

    def channels(self, name):
        """The scan-element channels of an IIO device, e.g. voltage0."""
        dev = self.iio_dev(name)
        out = self.out(f"ls {dev}/scan_elements")
        return sorted(set(re.findall(r"^in_(voltage\d+)_en$", out, re.M)))

    def dt(self, path):
        """A device tree property as text, or None if it does not exist."""
        rc, out = self.run(f"tr '\\0' ' ' < {path}", check=False)
        return out.strip() if rc == 0 else None

    def slot(self):
        return re.search(r"eicke\.slot=(\S+)", self.out("cat /proc/cmdline")).group(1)

    def env(self, name):
        return self.run(f"fw_printenv -n {name}", check=False)[1]


@pytest.fixture(scope="module")
def swu(build, deploy_dir):
    swu = deploy_dir / MACHINE / f"eicke-update-image-sdr-{MACHINE}.rootfs.swu"
    build(MACHINE, "eicke-update-image-sdr", needs=[swu])
    return swu


@pytest.fixture(scope="module")
def board(request):
    b = Sdr(request.config.getoption("--board"))
    b.wait_up(60)
    yield b
    # A step that failed with the variant switched.
    if b.env("ad936x_mode") == "1r1t":
        b.run("fw_setenv ad936x_mode", check=False)


@pytest.mark.incremental
class TestSdr:
    def test_board(self, board):
        assert board.out("hostname") == MACHINE
        assert "Fish Ball SDR" in board.dt("/proc/device-tree/model")
        assert board.slot() == board.env("rootdev")
        # U-Boot loaded the slot's bitstream (the FPGA manager reads DONE).
        assert board.out("cat /sys/class/fpga_manager/fpga0/state") == "operating"
        names = board.out("cat /sys/bus/iio/devices/iio:device*/name").split()
        for name in ("ad9361-phy", "cf-ad9361-lpc", "cf-ad9361-dds-core-lpc"):
            assert name in names
        assert board.out("systemctl is-active ad936x-config iiod") == "active\nactive"

    def test_mac_from_uboot_env(self, board):
        # fishball-sdr-mac's 02:..., or U-Boot's random one, when a saveenv
        # (a trial boot) stored it first: both locally administered unicast.
        mac = board.env("ethaddr").lower()
        assert re.fullmatch(r"[0-9a-f]{2}(:[0-9a-f]{2}){5}", mac), mac
        assert int(mac[:2], 16) & 0x03 == 0x02, mac
        assert board.out("cat /sys/class/net/eth0/address") == mac

    def test_rx_capture(self, board):
        # 64k complex samples of RX1+RX2 through the RX DMA (16-bit I/Q, so 8
        # bytes per sample): the receiver always sees some noise.
        board.run("iio_readdev -s 65536 cf-ad9361-lpc > /tmp/rx.bin", timeout=60)
        out = board.out(
            "python3 -c \"d = open('/tmp/rx.bin', 'rb').read(); "
            "print(len(d), sum(1 for b in d if b))\"; rm /tmp/rx.bin"
        )
        size, nonzero = map(int, out.split())
        assert size == 65536 * 8
        assert nonzero > size // 4

    def test_iiod_over_network(self, board):
        # iiod's text protocol: PRINT returns the context XML.
        with socket.create_connection((board.host, IIOD_PORT), timeout=10) as s:
            s.sendall(b"PRINT\r\n")
            f = s.makefile("rb")
            size = int(f.readline())
            assert size > 0
            xml = f.read(size).decode()
        assert 'name="ad9361-phy"' in xml
        assert 'name="cf-ad9361-lpc"' in xml

    def test_usb_gadget(self, board):
        g = "/sys/kernel/config/usb_gadget/fishball_sdr"
        assert board.out(f"cat {g}/UDC") == "ci_hdrc.0"  # ChipIdea on usb0
        ids = board.out(f"cat {g}/idVendor {g}/idProduct").split()
        assert ids == ["0x0456", "0xb673"]
        assert board.run("test -e /dev/iio_ffs/ep1", check=False)[0] == 0
        assert "inet 192.168.2.1/24" in board.out("ip -4 addr show usb0")

    def test_variant_1r1t(self, board):
        assert board.channels("cf-ad9361-lpc") == [f"voltage{i}" for i in range(4)]
        board.run("fw_setenv ad936x_mode 1r1t")
        board.reboot()
        assert board.dt(f"{PHY}/adi,2rx-2tx-mode-enable") is None
        assert board.dt(f"{DDS}/compatible") == "adi,axi-ad9364-dds-6.00.a"
        assert board.channels("cf-ad9361-lpc") == ["voltage0", "voltage1"]

    def test_variant_back_to_2r2t(self, board):
        board.run("fw_setenv ad936x_mode")
        board.reboot()
        assert board.dt(f"{PHY}/adi,2rx-2tx-mode-enable") == ""
        assert board.channels("cf-ad9361-lpc") == [f"voltage{i}" for i in range(4)]

    def test_update(self, board, swu):
        before = board.slot()
        other = "rootfs_b" if before == "rootfs_a" else "rootfs_a"
        board.install(swu, timeout=900)
        assert (board.env("rootdev"), board.env("ustate")) == (other, "1")
        board.reboot()
        assert board.slot() == other
        assert board.out("cat /sys/class/fpga_manager/fpga0/state") == "operating"
        wait_for(lambda: board.env("ustate") == "0", 120, what="the boot confirmed")
        type(self).slots = (before, other)

    def test_rollback(self, board):
        before, other = self.slots
        board.run("fw_setenv ustate 1 && fw_setenv bootcount 1")
        board.reboot()
        assert board.slot() == before
        assert board.env("ustate") == "3"
        board.run("fw_setenv ustate 0")

    def test_watchdog_resets_a_hung_kernel(self, board):
        # No panic reboot: only the watchdog gets the board out of this.
        crash = "sysctl -q kernel.panic=0 && (sleep 1; echo c > /proc/sysrq-trigger) &"
        board.run(crash, check=False)
        board.wait_up(240)
        assert board.out("cat /sys/class/fpga_manager/fpga0/state") == "operating"
