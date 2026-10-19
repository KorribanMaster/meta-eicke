"""qemuimx8mp-evk eicke-image-dev: Cortex-M7 rpmsg echo and A/B update.

QEMU cannot run U-Boot on this machine, so eicke-runqemu-imx8mp.sh boots
the kernel itself and picks the slot (SLOT=). Two boots share one disk
overlay: slot A installs the bundle from a second SD card, slot B must
confirm it.
"""

import os
import re
from pathlib import Path

import pytest
from eicke_qemu import Console, UBootEnv, wait_for

MACHINE = "qemuimx8mp-evk"
IMAGE = "eicke-image-dev"
RUNNER = Path(__file__).resolve().parent.parent / "eicke-runqemu-imx8mp.sh"


class Imx8mp:
    """Boots the wrapper with a disk overlay kept across boots."""

    def __init__(self, builddir, swu, tmp_path):
        self.builddir = builddir
        self.swu = swu
        self.overlay = tmp_path / "imx8mp-disk.qcow2"
        self.guest = None

    @property
    def log(self):
        return self.guest.log if self.guest else None

    def boot(self, slot, **extra):
        self.close()
        m7log = self.builddir / f"m7-uart4-{slot}.log"
        env = dict(os.environ, IMAGE=IMAGE, SLOT=slot, KEEP="1",
                   OVERLAY=str(self.overlay), M7LOG=str(m7log), **extra)  # fmt: skip
        self.guest = Console(
            ["bash", str(RUNNER)],
            self.builddir / f"qemu-serial-imx8mp-{slot}.log",
            env=env,
            cwd=self.builddir,
        )
        self.guest.login(MACHINE)
        assert f"eicke.slot={slot}" in self.guest.out("cat /proc/cmdline")
        return self.guest

    def close(self):
        if self.guest:
            self.guest.close()
            self.guest = None


@pytest.fixture(scope="module")
def imx(build, builddir, deploy_dir, tmp_path_factory):
    swu = deploy_dir / MACHINE / f"eicke-update-image-dev-{MACHINE}.rootfs.swu"
    build(MACHINE, IMAGE, "eicke-update-image-dev", "qemu-helper-native",
          needs=[swu])  # fmt: skip
    m = Imx8mp(builddir, swu, tmp_path_factory.mktemp("imx8mp"))
    yield m
    m.close()


def check_ptest(guest):
    rc, out = guest.run("ptest-runner rustamp-m7-echo", timeout=600, check=False)
    assert rc == 0 and re.search(r"PASS: \d+ round trips", out), out


@pytest.mark.incremental
class TestImx8mp:
    def test_slot_a_m7_echo(self, imx):
        check_ptest(imx.boot("rootfs_a", SWU=str(imx.swu)))

    def test_update(self, imx):
        g = imx.guest
        # The .swu card is the mmcblk without partitions.
        dev = g.out(
            "for d in /sys/block/mmcblk*; do"
            " [ -e $d/${d##*/}p1 ] || echo /dev/${d##*/}; done"
        )
        assert dev.startswith("/dev/mmcblk"), dev
        rc, out = g.run(f"swupdate -i {dev}", timeout=1200, check=False)
        assert "SWUPDATE successful" in out, out
        # QEMU wants a power-of-two SD card, so the .swu is padded: swupdate
        # installs up to the cpio trailer, then cannot hand the padding to the
        # installer ("Connection reset by peer") and exits 1.
        assert rc == 0 or "Connection reset by peer" in out, out
        env = UBootEnv(g)
        assert env.get("rootdev") == "rootfs_b"
        assert env.get("ustate") == "1"

    def test_slot_b_confirmed(self, imx):
        env = UBootEnv(imx.boot("rootfs_b"))
        wait_for(lambda: env.get("ustate") == "0", 120, what="ustate=0")

    def test_slot_b_m7_echo(self, imx):
        check_ptest(imx.guest)
