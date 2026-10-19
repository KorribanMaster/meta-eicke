"""qemuarm-uboot eicke-image-prod with verified boot: A/B over SSH.

Prod images only build with verified boot (EICKE_VERIFIED_BOOT=1: a signed
kernel FIT per slot, a signed .swu) and only log in over SSH with a key. The
image is always built here (mostly from sstate), with the switch and the test
key passed through the environment so the conf files stay untouched: any
bitbake run for qemuarm-uboot without the switch, runqemu's `bitbake -e`
included, sees eicke-image-prod as skipped and deletes its deployed image as
stale. Building needs the key store (EICKE_KEYS_DIR, /yocto/keys:
eicke-fit.{key,crt}, swupdate-priv.pem; scripts/eicke-gen-keys.sh).
"""

import os
from pathlib import Path

import pytest
from eicke_qemu import SshGuest, UBootEnv, wait_for

MACHINE = "qemuarm-uboot"


@pytest.fixture(scope="module")
def guest(build, builddir, deploy_dir, test_key):
    env = dict(
        os.environ,
        EICKE_VERIFIED_BOOT="1",
        EICKE_SSH_PUBKEY=Path(f"{test_key}.pub").read_text().strip(),
        BB_ENV_PASSTHROUGH_ADDITIONS=os.environ.get("BB_ENV_PASSTHROUGH_ADDITIONS", "")
        + " EICKE_VERIFIED_BOOT EICKE_SSH_PUBKEY",
    )
    swu = deploy_dir / MACHINE / f"eicke-update-image-prod-{MACHINE}.rootfs.swu"
    build(MACHINE, "eicke-image-prod", "eicke-update-image-prod", env=env,
          always=True, needs=[swu])  # fmt: skip
    # runqemu gets the same environment: it runs `bitbake -e eicke-image-prod`.
    g = SshGuest(
        ["runqemu", MACHINE, "eicke-image-prod", "wic", "nographic", "slirp",
         "snapshot", f"qemuparams=-drive file={swu},if=virtio,format=raw"],
        builddir / "qemu-serial-prod.log",
        test_key,
        env=env,
        cwd=builddir,
    )  # fmt: skip
    yield g
    g.close()


def check_fit(guest, slot, boots):
    """U-Boot booted the slot from /boot/fitImage, signed with eicke-fit.

    The bootcmd falls back to the plain zImage when a slot has no FIT, so
    the serial log must show `boots` FIT boots of the slot by now. U-Boot
    only checks the FIT hashes under QEMU (the key would live in its
    control DTB, which QEMU supplies; doc/security.md), so the signature is
    checked on the slot's own FIT.
    """
    banner = f"Eicke Linux ({slot}): booting signed /boot/fitImage"
    assert guest.log.read_text(errors="replace").count(banner) >= boots
    # The slot's /boot is hidden under the boot partition mounted on /boot;
    # a bind mount of / shows the rootfs's own /boot/fitImage.
    guest.run(
        "mkdir -p /tmp/slotroot && mount --bind / /tmp/slotroot &&"
        " grep -q eicke-fit /tmp/slotroot/boot/fitImage &&"
        " grep -q sha256,rsa2048 /tmp/slotroot/boot/fitImage;"
        " rc=$?; umount /tmp/slotroot; exit $rc"
    )


def mount_field(guest, mountpoint, field):
    """Field (1 = source, 3 = type, 4 = options) of a /proc/mounts entry."""
    return guest.out(f"awk '$2 == \"{mountpoint}\" {{print ${field}}}' /proc/mounts")


def assert_slot(guest, slot):
    assert f"eicke.slot={slot}" in guest.out("cat /proc/cmdline")


@pytest.mark.incremental
class TestProdAB:
    def test_boot_slot_a_ssh_key(self, guest):
        guest.wait_up()
        assert_slot(guest, "rootfs_a")
        check_fit(guest, "rootfs_a", 1)

    def test_rootfs_read_only(self, guest):
        assert mount_field(guest, "/", 4).startswith("ro,")

    def test_data_on_boot_disk(self, guest):
        assert mount_field(guest, "/etc", 3) == "overlay"
        # The overlayfs-etc preinit mounts /data from the boot disk (via
        # eicke-bootpart); the udev links must name the same partition.
        data = mount_field(guest, "/data", 1)
        assert data
        assert data == guest.out("/usr/libexec/eicke-bootpart data")
        assert data == guest.out("readlink -f /dev/eicke/data")

    def test_tampered_bundle_refused(self, guest):
        # sw-description and its .sig come first in the cpio: one MiB is
        # enough. Swapping two different bytes in sw-description keeps the
        # cpio (crc format) checksum valid, so the RSA check has to fail.
        # python3 because busybox dd has no conv=notrunc.
        swap = (
            'f = open("/data/tampered.swu", "r+b"); f.seek(300); b = f.read(2);'
            " assert b[0] != b[1]; f.seek(300); f.write(b[::-1]); f.close()"
        )
        guest.run(
            "dd if=/dev/vdb of=/data/tampered.swu bs=1M count=1 2>/dev/null"
            f" && python3 -c '{swap}'"
        )
        rc, out = guest.run("swupdate -v -c -i /data/tampered.swu", check=False)
        guest.run("rm -f /data/tampered.swu")
        assert rc != 0, "tampered bundle was accepted"
        assert "Error Verifying Data" in out, "refused, but not by the signature"

    def test_update(self, guest):
        env = UBootEnv(guest)
        assert env.get("ustate") == "0"
        assert "SWUPDATE successful" in guest.out("swupdate -i /dev/vdb", 900)
        assert env.get("rootdev") == "rootfs_b"
        assert env.get("ustate") == "1"

    def test_slot_b_confirmed(self, guest):
        guest.reboot()
        guest.wait_up()
        assert_slot(guest, "rootfs_b")
        check_fit(guest, "rootfs_b", 1)
        env = UBootEnv(guest)
        wait_for(lambda: env.get("ustate") == "0", 120, what="ustate=0")

    def test_rollback(self, guest):
        UBootEnv(guest).set(ustate=1, bootcount=1)
        guest.reboot()
        guest.wait_up()
        assert_slot(guest, "rootfs_a")
        check_fit(guest, "rootfs_a", 2)
        env = UBootEnv(guest)
        assert env.get("ustate") == "3"
        env.set(ustate=0)
