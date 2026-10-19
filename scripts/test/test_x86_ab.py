"""qemux86-64 eicke-image (GRUB, OVMF): A/B update, confirm, rollback."""

import pytest
from ab_cycle import ABCycle
from eicke_qemu import Console, GrubEnv

MACHINE = "qemux86-64"


@pytest.fixture(scope="module")
def guest(build, builddir, deploy_dir):
    swu = deploy_dir / MACHINE / f"eicke-update-image-{MACHINE}.rootfs.swu"
    build(MACHINE, "eicke-image", "eicke-update-image", "ovmf", needs=[swu])
    g = Console(
        ["runqemu", MACHINE, "eicke-image", "wic", "ovmf", "nographic", "kvm",
         "slirp", "snapshot", f"qemuparams=-drive file={swu},if=virtio,format=raw"],
        builddir / "qemu-serial-x86-ab.log",
        cwd=builddir,
    )  # fmt: skip
    yield g
    g.close()


class TestX86AB(ABCycle):
    hostname = MACHINE
    swu_dev = "/dev/vda"  # the wic disk is sda here
    env_class = GrubEnv
