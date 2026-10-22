#!/bin/sh
# Script of the eicke-update-bootloader bundle. SWUpdate runs it with
# "preinst" before it writes boot.bin.new and u-boot.itb.new to the ESP and
# with "postinst" after, and fails the update if it fails.
#
# The bootloader exists only once, so the switch has to be a single step:
# each SPL loads U-Boot from a file named after its build
# (u-boot-<hash>.itb, see the u-boot bbappend). The new .itb goes in under
# its own name next to the current one, which no boot.bin on the card loads,
# and the rename of boot.bin.new to boot.bin switches both files at once.
# Until that rename the old pair boots, after it the new pair.
#
# The replaced boot.bin is kept as BOOT0001.BIN, with its .itb: if boot.bin
# is torn (its boot header invalid), the boot ROM's golden image search
# boots BOOT0001.BIN. A boot.bin that loads but hangs is not caught: the
# card then needs BOOT0001.BIN copied back to boot.bin on another machine.
set -e
ESP=/boot

# The u-boot.itb file name the SPL in boot.bin $1 loads. Empty for a boot.bin
# from before the per-build names, whose SPL loads u-boot.itb.
spl_payload() {
    tr -c 'A-Za-z0-9.-' '\n' < "$1" | grep -x 'u-boot-[0-9a-f]\{16\}\.itb' | sort -u
}

case "$1" in
preinst)
    # The ESP must be mounted and hold the current bootloader.
    grep -q " $ESP vfat " /proc/mounts
    [ -f "$ESP/boot.bin" ]
    rm -f "$ESP/boot.bin.new" "$ESP/u-boot.itb.new" "$ESP/BOOT0001.BIN.tmp"
    ;;
postinst)
    [ -s "$ESP/boot.bin.new" ] && [ -s "$ESP/u-boot.itb.new" ]
    new=$(spl_payload "$ESP/boot.bin.new")
    [ "$(echo $new | wc -w)" -eq 1 ]
    cur=$(spl_payload "$ESP/boot.bin")
    [ -n "$cur" ] || cur=u-boot.itb
    [ -f "$ESP/$cur" ]

    # The same build again: nothing to switch, and BOOT0001.BIN keeps the
    # bootloader before it.
    if [ "$new" = "$cur" ]; then
        cmp -s "$ESP/u-boot.itb.new" "$ESP/$cur"
        rm -f "$ESP/boot.bin.new" "$ESP/u-boot.itb.new"
        echo "eicke bootloader: $new is installed already"
        exit 0
    fi

    # The new .itb under its name. It exists already only when the build is
    # the fallback (BOOT0001.BIN), with the same contents.
    if [ -e "$ESP/$new" ]; then
        cmp -s "$ESP/u-boot.itb.new" "$ESP/$new"
        rm -f "$ESP/u-boot.itb.new"
    else
        mv -f "$ESP/u-boot.itb.new" "$ESP/$new"
    fi
    cp "$ESP/boot.bin" "$ESP/BOOT0001.BIN.tmp"
    sync
    mv -f "$ESP/BOOT0001.BIN.tmp" "$ESP/BOOT0001.BIN"
    sync

    # The switch.
    mv -f "$ESP/boot.bin.new" "$ESP/boot.bin"
    sync

    # Drop the .itb files neither boot.bin nor BOOT0001.BIN loads, and the
    # *.old pair of the swap this script did before.
    for f in "$ESP"/u-boot*.itb; do
        case "${f##*/}" in
        "$new"|"$cur") ;;
        *) rm -f "$f" ;;
        esac
    done
    rm -f "$ESP/boot.bin.old" "$ESP/u-boot.itb.old"
    sync
    echo "eicke bootloader: boot.bin loads $new now (previous: BOOT0001.BIN, $cur)"
    ;;
esac
