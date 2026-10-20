#!/bin/sh
# Script of the eicke-update-bootloader bundle. SWUpdate runs it with
# "preinst" before it writes boot.bin.new and u-boot.itb.new to the ESP and
# with "postinst" after, and fails the update if it fails.
#
# The bootloader exists only once: keep the replaced pair as *.old (for a
# recovery from another machine) and swap u-boot.itb first, boot.bin last.
# In between, the old SPL loads the new u-boot.itb, which works: SPL only
# needs a valid FIT. Each mv is a single rename on the (sync-mounted) ESP.
set -e
ESP=/boot

case "$1" in
preinst)
    # The ESP must be mounted and hold the current bootloader.
    grep -q " $ESP vfat " /proc/mounts
    [ -f "$ESP/boot.bin" ] && [ -f "$ESP/u-boot.itb" ]
    rm -f "$ESP/boot.bin.new" "$ESP/u-boot.itb.new"
    ;;
postinst)
    [ -s "$ESP/boot.bin.new" ] && [ -s "$ESP/u-boot.itb.new" ]
    cp "$ESP/u-boot.itb" "$ESP/u-boot.itb.old"
    cp "$ESP/boot.bin" "$ESP/boot.bin.old"
    sync
    mv -f "$ESP/u-boot.itb.new" "$ESP/u-boot.itb"
    sync
    mv -f "$ESP/boot.bin.new" "$ESP/boot.bin"
    sync
    echo "eicke bootloader: boot.bin and u-boot.itb replaced (previous: *.old)"
    ;;
esac
