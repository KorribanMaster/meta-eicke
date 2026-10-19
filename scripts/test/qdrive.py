#!/usr/bin/env python3
"""Minimal pexpect-style driver for QEMU serial consoles (no deps).

Usage: qdrive.py <script.json>
The script is a JSON list of steps, each one of:
  {"expect": "<regex>", "timeout": <sec>}          wait for regex on serial
  {"send": "<text>"}                                 write text + newline
  {"sendraw": "<text>"}                              write text, no newline
  {"sleep": <sec>}                                   pause
The QEMU command is taken from the QEMU_CMD env var (shell string).
All serial I/O is mirrored to stdout and to QSERIAL_LOG (default
$BUILDDIR/qemu-serial.log, else ./qemu-serial.log).
Exits 0 if all steps complete, 1 on timeout/EOF.
"""
import json
import os
import pty
import re
import select
import shlex
import sys
import time

LOGPATH = os.environ.get(
    "QSERIAL_LOG", os.path.join(os.environ.get("BUILDDIR", "."), "qemu-serial.log")
)


def main():
    cmd = shlex.split(os.environ["QEMU_CMD"])
    with open(sys.argv[1]) as f:
        steps = json.load(f)
    logf = open(LOGPATH, "wb")
    pid, fd = pty.fork()
    if pid == 0:
        os.execvp(cmd[0], cmd)
        os._exit(127)

    buf = b""
    eof = False

    def pump(timeout):
        nonlocal buf, eof
        r, _, _ = select.select([fd], [], [], timeout)
        if fd in r:
            try:
                data = os.read(fd, 4096)
            except OSError:
                eof = True
                return
            if not data:
                eof = True
                return
            os.write(1, data)
            logf.write(data)
            logf.flush()
            buf += data

    rc = 0
    for step in steps:
        if "expect" in step:
            pat = re.compile(step["expect"].encode())
            end = time.monotonic() + step.get("timeout", 60)
            while True:
                m = pat.search(buf)
                if m:
                    sys.stderr.write("\n[qdrive] matched: %s\n" % step["expect"])
                    buf = buf[m.end() :]
                    break
                if time.monotonic() > end or eof:
                    sys.stderr.write(
                        "\n[qdrive] TIMEOUT/EOF waiting for: %s\n" % step["expect"]
                    )
                    rc = 1
                    return finish(pid, fd, logf, rc)
                pump(0.5)
        elif "send" in step:
            os.write(fd, (step["send"] + "\n").encode())
            time.sleep(0.3)
        elif "sendraw" in step:
            os.write(fd, step["sendraw"].encode())
            time.sleep(0.3)
        elif "sleep" in step:
            end = time.monotonic() + step["sleep"]
            while time.monotonic() < end:
                pump(0.5)
    # drain a little
    end = time.monotonic() + 3
    while time.monotonic() < end:
        pump(0.5)
    return finish(pid, fd, logf, rc)


def finish(pid, fd, logf, rc):
    try:
        os.write(fd, b"\n")
    except OSError:
        pass
    try:
        os.kill(pid, 9)
    except OSError:
        pass
    logf.close()
    sys.stderr.write("[qdrive] exit rc=%d\n" % rc)
    return rc


if __name__ == "__main__":
    sys.exit(main())
