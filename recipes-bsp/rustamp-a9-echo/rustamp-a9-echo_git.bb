SUMMARY = "RustAmp Cortex-A9 rpmsg echo firmware for CPU1 of a Zynq-7000 (ax7015b), built from source"
DESCRIPTION = "Bare-metal firmware from RustAmp (examples/cortex-a9-rtu-echo) \
for the second Cortex-A9 core: Linux runs on CPU0 (maxcpus=1) and the a9-rproc \
remoteproc driver loads this firmware and starts it on CPU1. It announces \
rpmsg-tty, so rpmsg_tty creates /dev/ttyRPMSG0, and echoes everything written \
there. Installed to /lib/firmware/rustamp-a9-echo.elf."

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=482e63bde56d4bd2f7c028e38cfcf0ef"

PV = "0.1+git"

# Linked to the carve-out at 0x3E000000 (top 32 MiB of the first GiB), which
# the board device tree reserves; built without debug UART (see the patch).
COMPATIBLE_MACHINE = "ax7015b"
A9_CARGO_FEATURES ?= "--features no-uart"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# Build-only dep: the prebuilt rustc/cargo with the armv7a-none-eabi core.
# Deliberately NOT 'inherit cargo' -- that drives OE's target rust for the
# Linux target triple.
DEPENDS = "rust-bin-thumbv7em-native"

# Public repo, fetched anonymously over https. Pinned to main after "example:
# run the ZynqMP R5 firmware on AMD/Xilinx QEMU". The patches add the no-uart
# feature (the AX7015B's UART0 is neither routed nor clocked; to be upstreamed
# to RustAmp) and drop the loom model checker, whose cfg(loom) dependency
# otherwise pulls ~30 crates.io packages into an offline cargo resolve. What
# remains are the workspace's own path crates, so cargo builds offline.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://0001-cortex-a9-rtu-echo-no-uart-feature-for-boards-without-a-spare-UART.patch \
           file://0002-cortex-a9-rtu-echo-build-offline-without-the-loom-model-checker.patch \
           file://run-ptest \
           file://rpmsg_echo_test.py \
"
SRCREV = "1acfdb0a43b12dd329da1525ccecfdd874c00f7a"

# wrynose unpacks git SRC_URIs to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

inherit ptest

# A bare-metal ARMv7-A blob, not code for the image's tune: must not be
# stripped/split or trigger an arch error. buildpaths: the release profile
# keeps debug info (remoteproc ignores it; handy for gdb over JTAG).
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} += "arch buildpaths"

RUST_BIN = "${STAGING_DATADIR_NATIVE}/rust-bin/bin"
FW_NAME = "rustamp-a9-echo.elf"

do_compile() {
    # build.rs is compiled for the build host and needs a single-program linker;
    # BUILD_CC carries flags, so wrap it.
    printf '#!/bin/sh\nexec %s "$@"\n' "${BUILD_CC}" > ${B}/build-cc
    chmod +x ${B}/build-cc

    unset RUSTFLAGS RUSTC RUSTDOC CARGO_BUILD_TARGET CARGO_HOME
    export PATH="${RUST_BIN}:$PATH"
    export CARGO_HOME="${WORKDIR}/cargo-home"
    export CARGO_TARGET_DIR="${B}"
    export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER="${B}/build-cc"

    # .cargo/config.toml in the example selects armv7a-none-eabi and -Trtu.x.
    cd ${S}/examples/cortex-a9-rtu-echo
    cargo build --release --frozen ${A9_CARGO_FEATURES}
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware
    install -m 0644 ${B}/armv7a-none-eabi/release/cortex-a9-rtu-echo \
        ${D}${nonarch_base_libdir}/firmware/${FW_NAME}
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/${FW_NAME}"

# The driver that loads it (the firmware is useless without it).
RDEPENDS:${PN} += "kernel-module-a9-rproc"

# ---------------------------------------------------------------------------
# ptest: start the firmware through remoteproc, run checked echo round trips
# over /dev/ttyRPMSG0, then stop and restart it (the park-page reload) and
# echo again. Enabled for just this recipe, like rustamp-m7-echo.
PTEST_ENABLED = "1"
# python3-io carries termios (raw tty mode); select/fcntl are in python3-core.
RDEPENDS:${PN}-ptest += "${PN} python3-core python3-io"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}
    install -m 0755 ${UNPACKDIR}/run-ptest ${D}${PTEST_PATH}/run-ptest
    install -m 0755 ${UNPACKDIR}/rpmsg_echo_test.py ${D}${PTEST_PATH}/rpmsg_echo_test.py
}
