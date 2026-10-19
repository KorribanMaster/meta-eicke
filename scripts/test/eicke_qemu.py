"""QEMU guest drivers for the eicke image tests.

- Console: a guest driven over its serial console (pexpect on a pty).
- SshGuest: a guest booted in the background (serial output to a log file)
  and driven over SSH, for prod images that have no serial login.
- bitbake(): a build step with the output kept in a log.
- UBootEnv / GrubEnv: read and write the A/B boot env from the guest.
"""

import os
import re
import signal
import subprocess
import time
from pathlib import Path

import pexpect

POWER_DOWN = r"Power down|System halted|reboot: Power|Requesting system poweroff"
RC_MARK = re.compile(r"@@RC=(\d+)@@")
# OSC (e.g. systemd's 3008 shell context, bash with systemd profile.d) and
# CSI terminal sequences the guest shell wraps around command output.
TERM_ESCAPES = re.compile(r"\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)|\x1b\[[0-?]*[ -/]*[@-~]")


def tail(path, lines=60):
    try:
        text = Path(path).read_text(errors="replace")
    except OSError:
        return ""
    return "\n".join(text.splitlines()[-lines:])


def wait_for(cond, timeout, interval=5, what="condition"):
    """Poll cond() until it returns a truthy value; return that value."""
    end = time.monotonic() + timeout
    while True:
        value = cond()
        if value:
            return value
        if time.monotonic() > end:
            raise AssertionError(f"timed out after {timeout}s waiting for {what}")
        time.sleep(interval)


def _stop_group(proc_pid):
    # runqemu starts qemu as a child: signal the whole session it leads.
    for sig in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(proc_pid, sig)
        except ProcessLookupError:
            return
        time.sleep(3)


def bitbake(builddir, machine, targets, env=None):
    """Build targets for machine; the output goes to a log in the build dir."""
    log = Path(builddir) / f"pytest-bitbake-{machine}.log"
    run_env = dict(os.environ if env is None else env, MACHINE=machine)
    with open(log, "w") as f:
        rc = subprocess.call(
            ["bitbake", *targets],
            env=run_env,
            cwd=builddir,
            stdout=f,
            stderr=subprocess.STDOUT,
        )
    assert rc == 0, f"bitbake {' '.join(targets)} failed:\n{tail(log)}"


class Console:
    """A guest on a serial console. Every byte it prints goes to log."""

    def __init__(self, cmd, log, env=None, cwd=None):
        self.log = Path(log)
        self._logf = open(self.log, "w")
        self.child = pexpect.spawn(
            cmd[0],
            cmd[1:],
            env=env,
            cwd=cwd,
            encoding="utf-8",
            codec_errors="replace",
            timeout=60,
            logfile=self._logf,
        )
        self.prompt = None

    def expect(self, pattern, timeout=60):
        """Wait for a regex; returns the match. Fails with the log tail."""
        try:
            self.child.expect(pattern, timeout=timeout)
        except (pexpect.TIMEOUT, pexpect.EOF) as e:
            why = "timeout" if isinstance(e, pexpect.TIMEOUT) else "QEMU exited"
            raise AssertionError(
                f"{why} waiting for {pattern!r} (log: {self.log})\n{tail(self.log)}"
            ) from None
        return self.child.match

    def sendline(self, line=""):
        self.child.sendline(line)

    def login(self, hostname, timeout=900):
        """Log in as root on the serial console (passwordless images only)."""
        self.prompt = re.escape(f"root@{hostname}:") + r"\S*# "
        self.expect(r"login:", timeout)
        self.sendline("root")
        self.expect(self.prompt)
        # No echo and no kernel messages: run() output is the command's own.
        self.sendline("stty -echo; dmesg -n 1")
        self.expect(self.prompt)

    def run(self, cmd, timeout=60, check=True):
        """Run a shell command in the guest; returns (rc, output)."""
        self.sendline(f"{cmd}; echo @@RC=$?@@")
        m = self.expect(RC_MARK, timeout)
        out = TERM_ESCAPES.sub("", self.child.before).replace("\r", "").strip()
        rc = int(m.group(1))
        self.expect(self.prompt)
        if check:
            assert rc == 0, f"{cmd!r} failed (rc={rc}):\n{out}"
        return rc, out

    def out(self, cmd, timeout=60):
        """Output of a command that must succeed."""
        return self.run(cmd, timeout)[1]

    def reboot(self):
        self.sendline("reboot")
        self.prompt = None

    def close(self, poweroff=True):
        if poweroff and self.child.isalive():
            try:
                self.sendline("poweroff")
                self.child.expect(POWER_DOWN, timeout=120)
            except (pexpect.TIMEOUT, pexpect.EOF, OSError):
                pass
        if self.child.isalive():
            _stop_group(self.child.pid)
        self.child.close(force=True)
        self._logf.close()


class SshGuest:
    """A guest started in the background and driven over SSH (port 2222)."""

    def __init__(self, cmd, log, key, env=None, cwd=None, port=2222):
        self.log = Path(log)
        self._logf = open(self.log, "w")
        self.proc = subprocess.Popen(
            cmd,
            env=env,
            cwd=cwd,
            stdin=subprocess.DEVNULL,
            stdout=self._logf,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        self.ssh_args = [
            "ssh",
            "-p",
            str(port),
            "-i",
            str(key),
            "-o",
            "StrictHostKeyChecking=no",
            "-o",
            "UserKnownHostsFile=/dev/null",
            "-o",
            "ConnectTimeout=5",
            "-o",
            "LogLevel=ERROR",
            "root@127.0.0.1",
        ]
        self.boot_id = None

    def run(self, cmd, timeout=120, check=True):
        """Run cmd in the guest; returns (rc, combined output)."""
        try:
            p = subprocess.run(
                [*self.ssh_args, cmd],
                stdin=subprocess.DEVNULL,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
        except subprocess.TimeoutExpired:
            if check:
                raise AssertionError(f"{cmd!r} timed out after {timeout}s") from None
            return -1, ""
        out = (p.stdout + p.stderr).strip()
        if check:
            assert p.returncode == 0, f"{cmd!r} failed (rc={p.returncode}):\n{out}"
        return p.returncode, out

    def out(self, cmd, timeout=120):
        return self.run(cmd, timeout)[1]

    def _current_boot_id(self):
        assert self.proc.poll() is None, f"QEMU exited (log: {self.log})"
        rc, out = self.run("cat /proc/sys/kernel/random/boot_id", 30, check=False)
        return out if rc == 0 and out != self.boot_id else None

    def wait_up(self, timeout=900):
        """Wait for SSH to answer from a new boot (not the one before reboot())."""
        self.boot_id = wait_for(self._current_boot_id, timeout, what="SSH login")

    def reboot(self):
        self.run("reboot", 30, check=False)

    def close(self):
        if self.proc.poll() is None:
            self.run("poweroff", 30, check=False)
            try:
                self.proc.wait(60)
            except subprocess.TimeoutExpired:
                pass
        if self.proc.poll() is None:
            _stop_group(self.proc.pid)
        self.proc.wait()
        self._logf.close()


class UBootEnv:
    """The U-Boot env, through libubootenv (fw_printenv/fw_setenv)."""

    def __init__(self, guest):
        self.guest = guest

    def get(self, name):
        rc, out = self.guest.run(f"fw_printenv -n {name}", check=False)
        return out if rc == 0 else None

    def set(self, **values):
        for k, v in values.items():
            self.guest.run(f"fw_setenv {k} {v}")


class GrubEnv:
    """The GRUB env block on the ESP."""

    PATH = "/boot/EFI/BOOT/grubenv"

    def __init__(self, guest):
        self.guest = guest

    def get(self, name):
        for line in self.guest.out(f"grub-editenv {self.PATH} list").splitlines():
            key, _, value = line.partition("=")
            if key == name:
                return value
        return None

    def set(self, **values):
        pairs = " ".join(f"{k}={v}" for k, v in values.items())
        self.guest.run(f"grub-editenv {self.PATH} set {pairs}")
