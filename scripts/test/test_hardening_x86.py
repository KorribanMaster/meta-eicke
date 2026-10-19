"""qemux86-64 eicke-image-prod: the runtime hardening.

Not part of the default run (marker `secureboot`, select it with
`-m secureboot`): the image has to be built beforehand with
EICKE_VERIFIED_BOOT and the x86 Secure Boot knobs from local.conf.sample
(doc/security.md), and with EICKE_SSH_PUBKEY = the test key's .pub.
"""

import subprocess
import warnings

import pytest
from eicke_qemu import SshGuest

MACHINE = "qemux86-64"

pytestmark = pytest.mark.secureboot


@pytest.fixture(scope="module")
def guest(build, builddir, deploy_dir, test_key):
    swu = deploy_dir / MACHINE / f"eicke-update-image-prod-{MACHINE}.rootfs.swu"
    build(MACHINE, needs=[swu])
    g = SshGuest(
        ["runqemu", MACHINE, "eicke-image-prod", "wic", "ovmf", "nographic", "kvm",
         "slirp", "snapshot", f"qemuparams=-drive file={swu},if=virtio,format=raw"],
        builddir / "qemu-serial-hardening.log",
        test_key,
        cwd=builddir,
    )  # fmt: skip
    g.wait_up()
    yield g
    g.close()


def test_sshd_policy(guest):
    out = guest.out("sshd -T 2>/dev/null")
    for setting in (
        "passwordauthentication no",
        "kbdinteractiveauthentication no",
        "permitrootlogin prohibit-password",
    ):
        assert setting in out.splitlines()


def test_password_login_refused(guest):
    rc = subprocess.call(
        ["ssh", "-p", "2222", "-o", "PreferredAuthentications=password",
         "-o", "PubkeyAuthentication=no", "-o", "StrictHostKeyChecking=no",
         "-o", "UserKnownHostsFile=/dev/null", "-o", "ConnectTimeout=5",
         "-o", "NumberOfPasswordPrompts=0", "-o", "LogLevel=ERROR",
         "root@127.0.0.1", "true"],
        stdin=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )  # fmt: skip
    assert rc != 0


def test_kernel_lockdown(guest):
    assert "[integrity]" in guest.out("cat /sys/kernel/security/lockdown")


def test_no_dev_mem_or_kcore(guest):
    guest.run("test ! -e /dev/mem && test ! -e /proc/kcore")


def test_modules_signed(guest):
    guest.run("modinfo r8169 | grep -qiE '^(sig_id|signer)'")


def test_sysctls(guest):
    names = (
        "kernel.yama.ptrace_scope kernel.dmesg_restrict kernel.kptr_restrict"
        " kernel.unprivileged_bpf_disabled net.ipv4.tcp_syncookies"
        " fs.protected_regular"
    )
    assert guest.out(f"sysctl -n {names}").split() == ["1", "1", "2", "1", "1", "2"]


def test_firewall(guest):
    assert guest.out("systemctl is-active eicke-firewall") == "active"
    assert "policy drop" in guest.out("nft list ruleset")


def test_swupdate_web_ui_allowed(guest):
    # 8080 (mongoose) listening and accepted by the ruleset; slirp only
    # forwards 2222->22, so there is no outside probe. No netstat/ss on the
    # image: 8080 = 0x1F90 in /proc/net/tcp, state 0A = LISTEN.
    guest.run("grep -qE ':1F90 .* 0A ' /proc/net/tcp /proc/net/tcp6")
    assert "dport 8080 accept" in guest.out("nft list chain inet filter input")


@pytest.mark.incremental
class TestHardwareCompatibility:
    def test_hwrevision(self, guest):
        assert guest.out("cat /etc/hwrevision") == f"eicke {MACHINE}"

    def test_bundle_for_other_hardware_refused(self, guest):
        # The CLI only says "Image invalid or corrupted" for this; that the
        # same bundle installs without the -H override is the discriminator.
        rc, out = guest.run(f"swupdate -H eicke:bogus-machine -i {swu_dev(guest)}",
                            300, check=False)  # fmt: skip
        assert "SWUPDATE successful" not in out
        assert "SWUPDATE failed" in out

    def test_bundle_installs(self, guest):
        assert "SWUPDATE successful" in guest.out(f"swupdate -i {swu_dev(guest)}", 900)


def swu_dev(guest):
    # The wic disk is sda; find the bundle drive by the cpio magic (070702:
    # swupdate builds the cpio in crc format).
    dev = guest.out(
        "for d in /dev/vda /dev/vdb; do [ -b $d ] &&"
        " head -c 6 $d | grep -qE '07070[12]' && echo $d; done; true"
    )
    assert dev, "no .swu drive in the guest"
    return dev.splitlines()[0]


def test_system_running(guest):
    rc, state = guest.run("systemctl is-system-running", check=False)
    assert state in ("running", "degraded")
    if state == "degraded":
        failed = guest.out("systemctl --failed --no-legend")
        warnings.warn(f"system degraded: {failed}")
