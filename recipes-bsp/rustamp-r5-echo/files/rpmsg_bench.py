#!/usr/bin/env python3
"""rpmsg latency and throughput benchmark for the RustAmp R5 echo firmware.

Runs on cores whose firmware is already running (rpmsg_echo_test.py starts
them) and reports, per core, alone and then with all cores at once:

  latency     round-trip time of one message (write, wait for its echo),
              for 1, 64 and 256 bytes: min / median / p99 / max in us;
  throughput  a stream of 256-byte blocks, written by one thread and read
              back and checked by another: echoed payload bytes per second
              (each byte crosses the link twice).

Measured from Python through rpmsg_tty, so the numbers include the tty
layer and the interpreter, not just the IPI and virtio path.

Exit status 0 when every echo came back intact; the numbers themselves are
informational.

Usage: rpmsg_bench.py [--cores 0,1] [--rounds N] [--seconds S]
"""
import argparse
import errno
import os
import sys
import threading
import time

from rpmsg_echo_test import Core, log, open_raw, read_exact

SIZES = (1, 64, 256)
BLOCK = 256


def latency(core, rounds, out):
    """Round-trip times per message size, in microseconds."""
    fd = open_raw(core.tty())
    res = {}
    try:
        for size in SIZES:
            msg = bytes(range(size)) if size <= 256 else bytes(size)
            times = []
            for _ in range(rounds):
                t0 = time.perf_counter()
                os.write(fd, msg)
                if read_exact(fd, size, 5) != msg:
                    out[core.index] = None
                    log("FAIL: %s: %d-byte echo broken" % (core.name, size))
                    return
                times.append((time.perf_counter() - t0) * 1e6)
            times.sort()
            res[size] = (times[0], times[len(times) // 2],
                         times[min(len(times) - 1, len(times) * 99 // 100)],
                         times[-1])
    finally:
        os.close(fd)
    out[core.index] = res


def throughput(core, seconds, out):
    """Echoed bytes per second for a continuous stream of BLOCK-byte writes."""
    fd = open_raw(core.tty())
    block = bytes((i * 7) & 0xFF for i in range(BLOCK))
    stop = threading.Event()
    sent = [0]
    ok = [True]

    def writer():
        while not stop.is_set():
            try:
                os.write(fd, block)
            except OSError as e:
                # rpmsg_tty does not block when all TX buffers are in
                # flight: it fails the write with ENOMEM. Back off and retry.
                if e.errno not in (errno.ENOMEM, errno.EAGAIN):
                    raise
                time.sleep(0.0002)
                continue
            sent[0] += BLOCK

    received = 0
    w = threading.Thread(target=writer)
    t0 = time.perf_counter()
    w.start()
    try:
        while time.perf_counter() - t0 < seconds:
            chunk = read_exact(fd, BLOCK, 5)
            if chunk != block:
                ok[0] = False
                break
            received += BLOCK
        elapsed = time.perf_counter() - t0
    finally:
        stop.set()
        w.join()
        # Drain what is still in flight, so the next run starts clean.
        while read_exact(fd, BLOCK, 0.5):
            pass
        os.close(fd)
    out[core.index] = (received / elapsed) if ok[0] else None
    if not ok[0]:
        log("FAIL: %s: stream echo broken after %d bytes"
            % (core.name, received))


def run(cores, fn, arg):
    out = {}
    threads = [threading.Thread(target=fn, args=(c, arg, out)) for c in cores]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return out


def report(label, cores, lat, thr):
    ok = True
    for c in cores:
        l, t = lat.get(c.index), thr.get(c.index)
        if l is None or t is None:
            ok = False
            continue
        for size in SIZES:
            mn, med, p99, mx = l[size]
            log("%s %s: latency %3d B: min %6.0f  median %6.0f  p99 %6.0f  "
                "max %6.0f us" % (label, c.name, size, mn, med, p99, mx))
        log("%s %s: throughput %.1f KiB/s echoed" % (label, c.name, t / 1024))
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cores", default="0,1")
    ap.add_argument("--rounds", type=int, default=1000,
                    help="round trips per message size")
    ap.add_argument("--seconds", type=float, default=5,
                    help="duration of each throughput run")
    args = ap.parse_args()

    cores = [Core(int(i)) for i in args.cores.split(",")]
    for c in cores:
        if not c.rproc or c.state() != "running" or not c.tty():
            log("FAIL: %s is not running with its ttyRPMSG" % c.name)
            return 1

    ok = True
    for c in cores:
        lat = run([c], latency, args.rounds)
        thr = run([c], throughput, args.seconds)
        ok &= report("alone", [c], lat, thr)
    if len(cores) > 1:
        lat = run(cores, latency, args.rounds)
        thr = run(cores, throughput, args.seconds)
        ok &= report("concurrent", cores, lat, thr)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
