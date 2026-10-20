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
    python3-pytest python3-mmap python3-pip \
    ptest-runner \
"

# The Zynq-RTU-over-PCIe test harness only exists on the x86 machines (the
# base image installs the driver there; see eicke-image.bb).
IMAGE_INSTALL:append:x86-64 = " zynq-pcie-rproc-ptest zynq-pcie-rproc-kicktool"

# QEMU i.MX 8M Plus: the Linux <-> Cortex-M7 rpmsg echo test
# (ptest-runner rustamp-m7-echo).
IMAGE_INSTALL:append:qemuimx8mp-evk = " rustamp-m7-echo-ptest"
IMAGE_INSTALL:append:ccimx8mn-dvk = " rustamp-m7-echo-ptest"
# ALINX AX7015B: the Linux <-> CPU1 rpmsg echo and reload test
# (ptest-runner rustamp-a9-echo).
IMAGE_INSTALL:append:ax7015b = " rustamp-a9-echo-ptest"
# Trenz TE0820: the Linux <-> R5_0/R5_1 rpmsg echo and reload test, both
# cores at once (ptest-runner rustamp-r5-echo).
IMAGE_INSTALL:append:te0820 = " rustamp-r5-echo-ptest"

# Debug symbols are intentionally NOT bundled (keeps the image curated). For
# source-level gdb either add  IMAGE_FEATURES += "dbg-pkgs"  (large), or use the
# wrynose debuginfod feature to fetch symbols on demand. For deeper profiling add
# IMAGE_FEATURES += "tools-profile"  (perf, valgrind, powertop, ...).
