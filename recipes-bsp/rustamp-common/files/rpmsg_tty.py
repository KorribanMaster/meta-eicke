"""Helpers shared by the RustAmp rpmsg echo tests and the R5 benchmark:
logging, polling and raw access to a /dev/ttyRPMSG<n>."""
import os
import select
import termios
import time


def log(msg):
    print("[rpmsg-echo] " + msg, flush=True)


def wait_for(cond, timeout):
    """Polls cond() until it is true (True) or timeout seconds pass (False)."""
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
    """Reads n bytes, or fewer if timeout seconds pass first."""
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
