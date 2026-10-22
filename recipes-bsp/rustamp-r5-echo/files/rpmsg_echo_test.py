#!/usr/bin/env python3
"""rpmsg echo test for the RustAmp Cortex-R5F firmware on both R5s (te0820).

Like rustamp-a9-echo's test, for several remotes at once:

  1. load /lib/firmware/rustamp-r5_<n>-echo.elf onto each selected R5 through
     remoteproc's sysfs interface and start it (unless it is already
     running);
  2. wait for each core's /dev/ttyRPMSG<m> (rpmsg_tty bound to that core's
     "rpmsg-tty" name-service announcement; matched to the core through
     sysfs, as the numbering follows the start order);
  3. on all cores at the same time, send COUNT messages of 1..256 bytes and
     check every echo byte for byte;
  4. CYCLES times: stop all cores (Linux resets the R5s), start them again
     and repeat 2 and 3.

Exit status 0 on success. The remotes are left running.

Usage: rpmsg_echo_test.py [--cores 0,1] [--count N] [--cycles N]
"""
import argparse
import glob
import os
import sys
import threading
import time

from rpmsg_tty import log, open_raw, read_exact, wait_for

MAX_MSG = 256
# The R5 cores' remoteproc instances, by device name (the split cluster's
# r5f@0 / r5f@1, named after their TCM addresses in the APU's view).
CORE_DEVICES = {0: "ffe00000.r5f", 1: "ffe90000.r5f"}


class Core:
    def __init__(self, index):
        self.index = index
        self.name = "r5_%d" % index
        self.firmware = "rustamp-r5_%d-echo.elf" % index
        self.rproc = None
        for d in sorted(glob.glob("/sys/class/remoteproc/remoteproc*")):
            with open(os.path.join(d, "name")) as f:
                if f.read().strip() == CORE_DEVICES[index]:
                    self.rproc = d

    def state(self):
        with open(os.path.join(self.rproc, "state")) as f:
            return f.read().strip()

    def set_state(self, state):
        with open(os.path.join(self.rproc, "state"), "w") as f:
            f.write(state)

    def start(self):
        if self.state() == "running":
            log("%s: %s already running" % (self.name, self.rproc))
            return
        with open(os.path.join(self.rproc, "firmware"), "w") as f:
            f.write(self.firmware)
        self.set_state("start")
        log("%s: started %s with %s" % (self.name, self.rproc, self.firmware))

    def tty(self):
        """This core's /dev/ttyRPMSG<m>, or None while there is none: the
        tty's sysfs device path runs through the core's remoteproc."""
        rp = "/%s/" % os.path.basename(self.rproc)
        for t in glob.glob("/sys/class/tty/ttyRPMSG*"):
            if rp in os.path.realpath(t):
                return "/dev/" + os.path.basename(t)
        return None


def echo_round_trips(core, count, results):
    """Waits for the core's tty and runs count checked echoes; stores True
    in results[core.index] on success."""
    results[core.index] = False
    t0 = time.monotonic()
    if not wait_for(lambda: core.tty() and os.path.exists(core.tty()), 10):
        log("FAIL: %s: no ttyRPMSG appeared" % core.name)
        return
    tty = core.tty()
    log("%s: %s is up after %d ms"
        % (core.name, tty, (time.monotonic() - t0) * 1000))

    fd = open_raw(tty)
    total = 0
    try:
        for i in range(count):
            n = 1 + (i * 37 + core.index * 101) % MAX_MSG
            msg = bytes((i + j + core.index) & 0xFF for j in range(n))
            os.write(fd, msg)
            echo = read_exact(fd, n, 5)
            if echo != msg:
                log("FAIL: %s: message %d (%d bytes): got %d bytes back%s"
                    % (core.name, i, n, len(echo),
                       "" if len(echo) != n else ", content differs"))
                return
            total += n
    finally:
        os.close(fd)
    log("%s: %d round trips, %d bytes echoed" % (core.name, count, total))
    results[core.index] = True


def echo_all(cores, count):
    """Runs the echo test on all cores concurrently; True if all pass."""
    results = {}
    threads = [threading.Thread(target=echo_round_trips,
                                args=(c, count, results)) for c in cores]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return all(results.get(c.index) for c in cores)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cores", default="0,1",
                    help="comma-separated R5 cores to test (default: 0,1)")
    ap.add_argument("--count", type=int, default=2000)
    ap.add_argument("--cycles", type=int, default=2,
                    help="stop/start reloads after the first run")
    args = ap.parse_args()

    cores = [Core(int(i)) for i in args.cores.split(",")]
    for c in cores:
        if not c.rproc:
            log("FAIL: no remoteproc instance for %s (%s)"
                % (c.name, CORE_DEVICES[c.index]))
            return 1
    for c in cores:
        c.start()
    if not echo_all(cores, args.count):
        return 1

    for cycle in range(1, args.cycles + 1):
        for c in cores:
            c.set_state("stop")
        for c in cores:
            if not wait_for(lambda: c.state() == "offline", 5):
                log("FAIL: reload %d: %s did not stop (state %s)"
                    % (cycle, c.name, c.state()))
                return 1
            if not wait_for(lambda: c.tty() is None, 5):
                log("FAIL: reload %d: %s's tty still there after stop"
                    % (cycle, c.name))
                return 1
        for c in cores:
            c.set_state("start")
        log("reload %d: stopped and restarted %s"
            % (cycle, ", ".join(c.name for c in cores)))
        if not echo_all(cores, args.count):
            return 1

    log("PASS: %d runs of %d round trips on %s at once"
        % (args.cycles + 1, args.count, ", ".join(c.name for c in cores)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
