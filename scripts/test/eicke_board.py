"""A real board on the network, driven over SSH as root (the board tests,
--board HOST): commands, reboots, and SWUpdate installs through its web
server (:8080)."""

import re
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

from eicke_qemu import wait_for


class Board:
    """A board on the network, driven over SSH as root."""

    def __init__(self, host, port=8080):
        self.host = host
        self.port = port
        self.ssh_args = [
            "ssh",
            "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=/dev/null",
            "-o", "ConnectTimeout=5",
            "-o", "LogLevel=ERROR",
            f"root@{host}",
        ]  # fmt: skip
        self.boot_id = None

    def run(self, cmd, timeout=120, check=True):
        """Run cmd on the board; returns (rc, combined output)."""
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

    def _new_boot_id(self):
        rc, out = self.run("cat /proc/sys/kernel/random/boot_id", 30, check=False)
        return out if rc == 0 and out != self.boot_id else None

    def wait_up(self, timeout=300):
        """Wait for SSH to answer from a new boot (not the one before reboot())."""
        self.boot_id = wait_for(self._new_boot_id, timeout, what=f"SSH on {self.host}")
        wait_for(
            lambda: self.run("systemctl is-system-running --wait", 120, check=False)[1]
            in ("running", "degraded"),
            120,
            what="systemd to finish booting",
        )

    def reboot(self):
        self.run("systemctl reboot", 30, check=False)
        self.wait_up()

    def install(self, swu, timeout=300):
        """Upload a .swu to SWUpdate's web server; returns SWUpdate's log of
        the install. Fails if the install fails."""
        cursor = self.out("journalctl -u swupdate -n 0 --show-cursor -q").split("cursor: ")[-1]
        boundary = uuid.uuid4().hex
        body = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="filename"; filename="{Path(swu).name}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode() + Path(swu).read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(
            f"http://{self.host}:{self.port}/upload",
            data=body,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        )
        with urllib.request.urlopen(req, timeout=timeout) as r:
            assert r.status == 200, f"upload of {swu}: HTTP {r.status}"

        def finished():
            log = self.out(f"journalctl -u swupdate -o cat --after-cursor='{cursor}'")
            if re.search(r"SWUPDATE successful|SWUPDATE failed|\[ERROR\]", log):
                return log
            return None

        log = wait_for(finished, timeout, interval=2, what=f"SWUpdate to install {swu}")
        assert "SWUPDATE successful" in log, f"install of {swu} failed:\n{log}"
        time.sleep(2)  # postupdate, then the daemon is idle again
        return log
