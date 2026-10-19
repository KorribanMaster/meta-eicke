"""The A/B cycle on a serial-console guest, shared by the machines.

A subclass sets the class attributes and provides a module-scoped `guest`
fixture: a freshly started Console, not yet logged in.
"""

import pytest
from eicke_qemu import wait_for


@pytest.mark.incremental
class ABCycle:
    hostname = None  # the guest's prompt: root@<hostname>:~#
    swu_dev = None  # the attached .swu as the guest sees it
    env_class = None  # UBootEnv or GrubEnv
    slot_banner = False  # the bootloader prints "Eicke Linux (<slot>)"
    first_boot_markers = ()  # regexes the first boot must print, in order

    def booted(self, guest, slot):
        if self.slot_banner:
            guest.expect(rf"Eicke Linux \({slot}\)", 300)
        guest.login(self.hostname)
        assert f"eicke.slot={slot}" in guest.out("cat /proc/cmdline")
        return self.env_class(guest)

    def test_boot_slot_a(self, guest):
        for marker in self.first_boot_markers:
            guest.expect(marker, 120)
        env = self.booted(guest, "rootfs_a")
        assert env.get("rootdev") == "rootfs_a"
        assert env.get("ustate") == "0"

    def test_update(self, guest):
        out = guest.out(f"swupdate -i {self.swu_dev}", timeout=900)
        assert "SWUPDATE successful" in out
        env = self.env_class(guest)
        assert env.get("rootdev") == "rootfs_b"
        assert env.get("ustate") == "1"

    def test_slot_b_confirmed(self, guest):
        guest.reboot()
        env = self.booted(guest, "rootfs_b")
        wait_for(lambda: env.get("ustate") == "0", 120, what="ustate=0")

    def test_rollback(self, guest):
        self.env_class(guest).set(ustate=1, bootcount=1)
        guest.reboot()
        env = self.booted(guest, "rootfs_a")
        assert env.get("ustate") == "3"
        env.set(ustate=0)
