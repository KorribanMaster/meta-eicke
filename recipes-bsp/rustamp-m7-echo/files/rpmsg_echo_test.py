#!/usr/bin/env python3
"""rpmsg echo test for the RustAmp Cortex-M7 firmware (QEMU imx8mp-evk).

Port of RustAmp examples/imx8mn-m7-echo/qemu/init.c to a regular rootfs:

  1. load /lib/firmware/<FIRMWARE> onto the M7 through remoteproc's sysfs
     interface and start it (unless it is already running);
  2. wait for /dev/ttyRPMSG0 (rpmsg_tty bound to the firmware's "rpmsg-tty"
     name-service announcement);
  3. send COUNT messages of 1..256 bytes and check every echo byte for byte.

Exit status 0 on success. The remote is left running: the QEMU M7 model does
not reset the core on a second start, so stop/start cycles are not tested.

Usage: rpmsg_echo_test.py [--count N] [--firmware NAME] [--rproc DIR]
"""
import argparse
import glob
import os
import select
import sys
import termios
import time

TTY = "/dev/ttyRPMSG0"
MAX_MSG = 256


def log(msg):
    print("[rpmsg-echo] " + msg, flush=True)


def find_rproc(name_hints=("cm7", "imx-rproc")):
    """remoteproc instance of the M7: its name is "imx-rproc" on recent
    kernels (older ones used e.g. "imx8mp-cm7"); falls back to the only
    instance when there is exactly one."""
    dirs = sorted(glob.glob("/sys/class/remoteproc/remoteproc*"))
    for d in dirs:
        with open(os.path.join(d, "name")) as f:
            name = f.read()
        if any(h in name for h in name_hints):
            return d
    return dirs[0] if len(dirs) == 1 else None


def start_remote(rproc, firmware):
    with open(os.path.join(rproc, "state")) as f:
        state = f.read().strip()
    if state == "running":
        log("%s already running" % rproc)
        return
    with open(os.path.join(rproc, "firmware"), "w") as f:
        f.write(firmware)
    with open(os.path.join(rproc, "state"), "w") as f:
        f.write("start")
    log("started %s with %s" % (rproc, firmware))


def wait_for(path, timeout):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if os.path.exists(path):
            return True
        time.sleep(0.05)
    return False


def open_raw(path):
    fd = os.open(path, os.O_RDWR | os.O_NOCTTY)
    attrs = termios.tcgetattr(fd)
    # cfmakeraw: no line discipline processing, no echo, 8-bit clean.
    attrs[0] &= ~(termios.IGNBRK | termios.BRKINT | termios.PARMRK |
                  termios.ISTRIP | termios.INLCR | termios.IGNCR |
                  termios.ICRNL | termios.IXON)
    attrs[1] &= ~termios.OPOST
    attrs[2] &= ~(termios.CSIZE | termios.PARENB)
    attrs[2] |= termios.CS8
    attrs[3] &= ~(termios.ECHO | termios.ECHONL | termios.ICANON |
                  termios.ISIG | termios.IEXTEN)
    termios.tcsetattr(fd, termios.TCSANOW, attrs)
    termios.tcflush(fd, termios.TCIOFLUSH)
    return fd


def read_exact(fd, n, timeout):
    buf = b""
    end = time.monotonic() + timeout
    while len(buf) < n:
        left = end - time.monotonic()
        if left <= 0:
            break
        r, _, _ = select.select([fd], [], [], left)
        if r:
            buf += os.read(fd, n - len(buf))
    return buf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=2000)
    ap.add_argument("--firmware", default="rustamp-echo.elf")
    ap.add_argument("--rproc", help="remoteproc sysfs dir (default: the cm7)")
    args = ap.parse_args()

    rproc = args.rproc or find_rproc()
    if not rproc:
        log("FAIL: no Cortex-M7 remoteproc instance found")
        return 1
    start_remote(rproc, args.firmware)

    t0 = time.monotonic()
    if not wait_for(TTY, 10):
        log("FAIL: %s did not appear" % TTY)
        return 1
    log("%s is up after %d ms" % (TTY, (time.monotonic() - t0) * 1000))

    fd = open_raw(TTY)
    total = 0
    try:
        for i in range(args.count):
            n = 1 + (i * 37) % MAX_MSG
            msg = bytes((i + j) & 0xFF for j in range(n))
            os.write(fd, msg)
            echo = read_exact(fd, n, 5)
            if echo != msg:
                log("FAIL: message %d (%d bytes): got %d bytes back%s"
                    % (i, n, len(echo),
                       "" if len(echo) != n else ", content differs"))
                return 1
            total += n
    finally:
        os.close(fd)

    log("PASS: %d round trips, %d bytes echoed by the RustAmp firmware"
        % (args.count, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
