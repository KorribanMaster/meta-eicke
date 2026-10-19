#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Flatten the loadable segments of a 32-bit little-endian ELF into a raw image.

U-Boot's mkimage (-T zynqmpimage, CONFIG_PMUFW_INIT_FILE) wants the PMU
firmware as the raw image the boot ROM copies to PMU RAM, while the Trenz
reference design ships it as pmufw.elf (MicroBlaze). There is no MicroBlaze
objcopy in the build, so this does what `objcopy -O binary` does: place each
PT_LOAD segment's file bytes at its physical address relative to the lowest
one and zero-fill the gaps between them.

usage: elf2bin.py <in.elf> <out.bin>
"""
import struct
import sys

PT_LOAD = 1


def main(src, dst):
    data = open(src, "rb").read()
    if data[:4] != b"\x7fELF" or data[4] != 1 or data[5] != 1:
        sys.exit(f"{src}: not a 32-bit little-endian ELF")
    e_phoff, = struct.unpack_from("<I", data, 0x1C)
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x2A)

    segs = []
    for i in range(e_phnum):
        (p_type, p_offset, _vaddr, p_paddr, p_filesz, _memsz, _flags,
         _align) = struct.unpack_from("<8I", data, e_phoff + i * e_phentsize)
        if p_type == PT_LOAD and p_filesz:
            segs.append((p_paddr, data[p_offset:p_offset + p_filesz]))
    if not segs:
        sys.exit(f"{src}: no loadable segments")

    segs.sort()
    base = segs[0][0]
    out = bytearray()
    for paddr, blob in segs:
        off = paddr - base
        if off < len(out):
            sys.exit(f"{src}: overlapping segments at {paddr:#x}")
        out += bytes(off - len(out)) + blob
    open(dst, "wb").write(out)
    print(f"{dst}: {len(out)} bytes from {base:#x}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
