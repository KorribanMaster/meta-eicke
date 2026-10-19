#!/usr/bin/env python3
"""rpmsg echo test for the RustAmp Cortex-A9 firmware on CPU1 (ax7015b).

Like rustamp-m7-echo's test, plus reloads:

  1. load /lib/firmware/<FIRMWARE> onto CPU1 through remoteproc's sysfs
     interface and start it (unless it is already running);
  2. wait for /dev/ttyRPMSG0 (rpmsg_tty bound to the firmware's "rpmsg-tty"
     name-service announcement);
  3. send COUNT messages of 1..256 bytes and check every echo byte for byte;
  4. CYCLES times: stop the remote (the firmware parks itself in its park
     page), start it again and repeat 2 and 3.

Exit status 0 on success. The remote is left running.

Usage: rpmsg_echo_test.py [--count N] [--cycles N] [--firmware NAME] [--rproc DIR]
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


def find_rproc(name_hints=("remoteproc-cpu1",)):
    """remoteproc instance of CPU1 (named after its device tree node);
    falls back to the only instance when there is exactly one."""
    dirs = sorted(glob.glob("/sys/class/remoteproc/remoteproc*"))
    for d in dirs:
        with open(os.path.join(d, "name")) as f:
            name = f.read()
        if any(h in name for h in name_hints):
            return d
    return dirs[0] if len(dirs) == 1 else None


def rproc_state(rproc):
    with open(os.path.join(rproc, "state")) as f:
        return f.read().strip()


def set_state(rproc, state):
    with open(os.path.join(rproc, "state"), "w") as f:
        f.write(state)


def start_remote(rproc, firmware):
    if rproc_state(rproc) == "running":
        log("%s already running" % rproc)
        return
    with open(os.path.join(rproc, "firmware"), "w") as f:
        f.write(firmware)
    set_state(rproc, "start")
    log("started %s with %s" % (rproc, firmware))


def wait_for(cond, timeout):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if cond():
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


def echo_round_trips(count):
    """Waits for the tty and runs count checked echoes; True on success."""
    t0 = time.monotonic()
    if not wait_for(lambda: os.path.exists(TTY), 10):
        log("FAIL: %s did not appear" % TTY)
        return False
    log("%s is up after %d ms" % (TTY, (time.monotonic() - t0) * 1000))

    fd = open_raw(TTY)
    total = 0
    try:
        for i in range(count):
            n = 1 + (i * 37) % MAX_MSG
            msg = bytes((i + j) & 0xFF for j in range(n))
            os.write(fd, msg)
            echo = read_exact(fd, n, 5)
            if echo != msg:
                log("FAIL: message %d (%d bytes): got %d bytes back%s"
                    % (i, n, len(echo),
                       "" if len(echo) != n else ", content differs"))
                return False
            total += n
    finally:
        os.close(fd)
    log("%d round trips, %d bytes echoed" % (count, total))
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=2000)
    ap.add_argument("--cycles", type=int, default=2,
                    help="stop/start reloads after the first run")
    ap.add_argument("--firmware", default="rustamp-a9-echo.elf")
    ap.add_argument("--rproc", help="remoteproc sysfs dir (default: CPU1's)")
    args = ap.parse_args()

    rproc = args.rproc or find_rproc()
    if not rproc:
        log("FAIL: no Cortex-A9 remoteproc instance found")
        return 1
    start_remote(rproc, args.firmware)
    if not echo_round_trips(args.count):
        return 1

    for cycle in range(1, args.cycles + 1):
        set_state(rproc, "stop")
        if not wait_for(lambda: rproc_state(rproc) == "offline", 5):
            log("FAIL: reload %d: %s did not stop (state %s)"
                % (cycle, rproc, rproc_state(rproc)))
            return 1
        if not wait_for(lambda: not os.path.exists(TTY), 5):
            log("FAIL: reload %d: %s still there after stop" % (cycle, TTY))
            return 1
        set_state(rproc, "start")
        log("reload %d: stopped and restarted %s" % (cycle, rproc))
        if not echo_round_trips(args.count):
            return 1

    log("PASS: %d runs of %d round trips with the RustAmp A9 firmware"
        % (args.cycles + 1, args.count))
    return 0


if __name__ == "__main__":
    sys.exit(main())
