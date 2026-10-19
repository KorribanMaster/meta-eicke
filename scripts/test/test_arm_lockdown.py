"""qemuarm-uboot eicke-image: the U-Boot env cannot change how it boots.

The boot logic is compiled into U-Boot and the env is whitelisted, so a
bootcmd or a cmdline smuggled in through rootdev written from Linux must
not take effect.
"""

import pytest
from eicke_qemu import Console

MACHINE = "qemuarm-uboot"


@pytest.fixture(scope="module")
def guest(build, builddir, deploy_dir):
    wic = deploy_dir / MACHINE / f"eicke-image-{MACHINE}.rootfs.wic"
    build(MACHINE, "eicke-image", needs=[wic])
    g = Console(
        ["runqemu", MACHINE, "eicke-image", "wic", "nographic", "slirp", "snapshot"],
        builddir / "qemu-serial-arm-lockdown.log",
        cwd=builddir,
    )
    yield g
    g.close()


@pytest.mark.incremental
class TestEnvLockdown:
    def test_tamper_env(self, guest):
        guest.expect(r"Eicke Linux \(rootfs_a\)", 120)
        guest.login(MACHINE)
        guest.run("fw_setenv bootcmd 'echo PWNED && sleep 1000'")
        guest.run("fw_setenv rootdev 'rootfs_a init=/bin/sh'")

    def test_env_ignored(self, guest):
        guest.reboot()
        guest.expect(r"Eicke Linux \(rootfs_a\)", 300)
        assert "PWNED" not in guest.child.before
        guest.login(MACHINE)
        assert "init=" not in guest.out("cat /proc/cmdline")
