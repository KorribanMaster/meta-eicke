"""qemuarm-uboot eicke-image: A/B update, confirm, rollback (U-Boot)."""

import pytest
from ab_cycle import ABCycle
from eicke_qemu import Console, UBootEnv

MACHINE = "qemuarm-uboot"


@pytest.fixture(scope="module")
def guest(build, builddir, deploy_dir):
    swu = deploy_dir / MACHINE / f"eicke-update-image-{MACHINE}.rootfs.swu"
    build(MACHINE, "eicke-image", "eicke-update-image", needs=[swu])
    g = Console(
        ["runqemu", MACHINE, "eicke-image", "wic", "nographic", "slirp", "snapshot",
         f"qemuparams=-drive file={swu},if=virtio,format=raw"],
        builddir / "qemu-serial-arm-ab.log",
        cwd=builddir,
    )  # fmt: skip
    yield g
    g.close()


class TestArmAB(ABCycle):
    hostname = MACHINE
    swu_dev = "/dev/vdb"
    env_class = UBootEnv
    slot_banner = True
    first_boot_markers = ("U-Boot 20", r"Loading Environment from FAT\.\.\. OK")
