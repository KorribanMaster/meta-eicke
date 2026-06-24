SUMMARY = "Eicke development image: eicke-image + on-target debug tools"
DESCRIPTION = "Development variant of eicke-image (same GRUB-EFI A/B + SWUpdate \
base) with a curated set of on-target debugging/diagnostic tools. Use this for \
hardware bring-up; the lean eicke-image stays the production/OTA base."
LICENSE = "MIT"

require recipes-core/images/eicke-image.bb

# tools-debug -> gdb + gdbserver + strace (+ libc-mtrace)
# package-management -> rpm on target, so more tools can be installed at runtime
IMAGE_FEATURES += "tools-debug package-management"

# Curated debug toolset (all from layers already in bblayers: oe-core, meta-oe,
# meta-networking). Full util-linux/e2fsprogs replace the busybox applets, so
# findmnt/lsblk/fsck etc. are available.
IMAGE_INSTALL:append = " \
    usbutils pciutils i2c-tools dmidecode devmem2 evtest \
    minicom \
    ltrace \
    systemd-analyze \
    tcpdump ethtool iproute2 iproute2-ss curl \
    procps htop lsof util-linux e2fsprogs file \
    vim less \
    python3-pytest python3-pip \
"

# Debug symbols are intentionally NOT bundled (keeps the image curated). For
# source-level gdb either add  IMAGE_FEATURES += "dbg-pkgs"  (large), or use the
# wrynose debuginfod feature to fetch symbols on demand. For deeper profiling add
# IMAGE_FEATURES += "tools-profile"  (perf, valgrind, powertop, ...).
