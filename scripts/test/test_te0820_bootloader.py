"""te0820 on the bench: bootloader updates (eicke-update-bootloader) over SSH.

Runs against a real board running eicke-image (pass --board HOST): the
bundles are uploaded to SWUpdate's web server (:8080), everything else goes
over SSH as root. Which bootloader booted is read from Linux: U-Boot's
version carries the same 16-digit build id as the u-boot-<id>.itb its SPL
loads (u-boot bbappend), and U-Boot passes it as /chosen/u-boot,version.

Two bootloader builds are needed: the current one (deploy dir) and a second
one that differs only in its U-Boot version suffix (UBOOT_LOCALVERSION=-alt),
kept as $BUILDDIR/te0820-bootloader-alt.swu. --build builds both. The steps:

  1. update to the current build, reboot into it;
  2. update to the alt build: the current boot.bin becomes BOOT0001.BIN,
     reboot into alt;
  3. the same bundle again changes nothing;
  4. a boot.bin with a broken boot header: the boot ROM falls back to
     BOOT0001.BIN (the current build);
  5. boot.bin repaired: the next reboot boots it again (SPL resets CSU
     MULTI_BOOT after a fallback, u-boot patch 0003);
  6. update back to the current build.

The board ends on the current build with the alt build as its fallback. It
reboots six times; a step that leaves it unbootable needs the serial console
or the microSD card (doc/machines/te0820.md).
"""

import os
import re
import shutil
from pathlib import Path

import pytest
from eicke_board import Board

MACHINE = "te0820"
ESP = "/boot"
BUILD_ID = re.compile(rb"u-boot-([0-9a-f]{16})\.itb")
# What eicke-bootloader-swap.sh reads from a boot.bin: the .itb its SPL loads.
SPL_PAYLOAD = r"tr -c 'A-Za-z0-9.-' '\n' < {} | grep -x 'u-boot-[0-9a-f]\{{16\}}\.itb' | sort -u"
BACKUP = "/data/boot.bin.pytest"

pytestmark = pytest.mark.board


class Te0820(Board):
    """The te0820 on the bench: which bootloader build booted, and the
    bootloader files on its ESP."""

    def running_build(self):
        """The build id of the U-Boot that booted Linux."""
        version = self.out("tr -d '\\0' < /proc/device-tree/chosen/u-boot,version")
        m = re.search(r"-([0-9a-f]{16})\b", version)
        assert m, f"no build id in U-Boot version {version!r} (U-Boot from before it)"
        return m.group(1)

    def spl_build(self, path):
        """The build id of the .itb the SPL in boot.bin `path` loads, or None."""
        out = self.out(SPL_PAYLOAD.format(path) + " || true")
        m = BUILD_ID.search(out.encode())
        return m.group(1).decode() if m else None

    def esp(self):
        """{file name: md5} of the bootloader files on the ESP."""
        out = self.out(f"cd {ESP} && md5sum boot.bin BOOT0001.BIN u-boot*.itb 2>/dev/null || true")
        return {name: md5 for md5, name in (line.split() for line in out.splitlines())}


def bundle_build(swu):
    """The build id of the bootloader in a .swu (the .itb its boot.bin's SPL
    loads), from the cpio (newc) archive."""
    data = Path(swu).read_bytes()
    off = 0
    while off + 110 <= len(data):
        hdr = data[off : off + 110]
        assert hdr[:6] in (b"070701", b"070702"), f"{swu}: not a newc cpio archive"
        filesize = int(hdr[54:62], 16)
        namesize = int(hdr[94:102], 16)
        name = data[off + 110 : off + 110 + namesize - 1].decode()
        off = (off + 110 + namesize + 3) & ~3
        if name == "boot.bin":
            m = BUILD_ID.search(data[off : off + filesize])
            assert m, f"{swu}: boot.bin names no u-boot-<id>.itb"
            return m.group(1).decode()
        if name == "TRAILER!!!":
            break
        off = (off + filesize + 3) & ~3
    raise AssertionError(f"{swu}: no boot.bin")


@pytest.fixture(scope="module")
def bundles(request, build, builddir, deploy_dir):
    """(current, alt): the two bootloader bundles, with their build ids."""
    current = deploy_dir / MACHINE / f"eicke-update-bootloader-{MACHINE}.rootfs.swu"
    alt = builddir / f"{MACHINE}-bootloader-alt.swu"
    if request.config.getoption("--build"):
        env = dict(
            os.environ,
            UBOOT_LOCALVERSION="-alt",
            BB_ENV_PASSTHROUGH_ADDITIONS=os.environ.get("BB_ENV_PASSTHROUGH_ADDITIONS", "")
            + " UBOOT_LOCALVERSION",
        )
        build(MACHINE, "eicke-update-bootloader", env=env, always=True, needs=[current])
        shutil.copyfile(current.resolve(), alt)
    # Always last, so the deploy dir holds the current build again.
    build(MACHINE, "eicke-update-bootloader", needs=[current, alt])
    cur_id, alt_id = bundle_build(current), bundle_build(alt)
    assert cur_id != alt_id, f"{alt} is the current build: rebuild it with --build"
    return (current, cur_id), (alt, alt_id)


@pytest.fixture(scope="module")
def board(request):
    host = request.config.getoption("--board")
    b = Te0820(host)
    b.wait_up(60)
    yield b
    # A step that failed between breaking boot.bin and repairing it.
    b.run(f"[ ! -f {BACKUP} ] || {{ cp {BACKUP} {ESP}/boot.bin && sync && rm {BACKUP}; }}",
          check=False)  # fmt: skip


@pytest.mark.incremental
class TestBootloaderUpdate:
    def test_board(self, board):
        assert board.out(f"grep ' {ESP} vfat ' /proc/mounts")
        assert board.out("hostname") == MACHINE
        type(self).slot = (board.out("fw_printenv -n rootdev"),
                           board.out("fw_printenv -n ustate"))  # fmt: skip

    def test_update_to_current(self, board, bundles):
        (swu, cur), _ = bundles
        log = board.install(swu)
        assert re.search(f"u-boot-{cur}.itb (now|is installed already)", log)
        assert board.spl_build(f"{ESP}/boot.bin") == cur
        board.reboot()
        assert board.running_build() == cur

    def test_update_to_alt(self, board, bundles):
        (_, cur), (swu, alt) = bundles
        before = board.esp()
        log = board.install(swu)
        assert f"boot.bin loads u-boot-{alt}.itb now" in log
        after = board.esp()
        # The current boot.bin is the fallback now; only the two builds' .itb
        # files are left.
        assert after["BOOT0001.BIN"] == before["boot.bin"]
        assert board.spl_build(f"{ESP}/BOOT0001.BIN") == cur
        assert board.spl_build(f"{ESP}/boot.bin") == alt
        assert sorted(f for f in after if f.endswith(".itb")) == sorted(
            [f"u-boot-{cur}.itb", f"u-boot-{alt}.itb"]
        )
        # The A/B state is left alone (bootloader_state_marker = false).
        assert (board.out("fw_printenv -n rootdev"),
                board.out("fw_printenv -n ustate")) == self.slot  # fmt: skip
        board.reboot()
        assert board.running_build() == alt

    def test_same_bundle_again(self, board, bundles):
        _, (swu, alt) = bundles
        before = board.esp()
        log = board.install(swu)
        assert f"u-boot-{alt}.itb is installed already" in log
        assert board.esp() == before

    def test_fallback_to_boot0001(self, board, bundles):
        (_, cur), _ = bundles
        # Break the boot header's identification word ("XNLX" at 0x24).
        board.run(
            f"cp {ESP}/boot.bin {BACKUP} && python3 -c \""
            f"f = open('{ESP}/boot.bin', 'r+b'); f.seek(0x24); "
            "assert f.read(4) == b'XNLX'; f.seek(0x24); f.write(b'XXXX'); f.close()\""
            " && sync"
        )
        board.reboot()
        assert board.running_build() == cur

    def test_back_to_boot_bin(self, board, bundles):
        _, (_, alt) = bundles
        board.run(f"cp {BACKUP} {ESP}/boot.bin && sync && rm {BACKUP}")
        # Without SPL resetting MULTI_BOOT, the boot ROM would stay on
        # BOOT0001.BIN until a power-on reset.
        board.reboot()
        assert board.running_build() == alt

    def test_update_back_to_current(self, board, bundles):
        (swu, cur), (_, alt) = bundles
        log = board.install(swu)
        assert f"boot.bin loads u-boot-{cur}.itb now" in log
        assert board.spl_build(f"{ESP}/BOOT0001.BIN") == alt
        board.reboot()
        assert board.running_build() == cur
