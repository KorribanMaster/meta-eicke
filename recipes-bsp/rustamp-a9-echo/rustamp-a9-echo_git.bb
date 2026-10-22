SUMMARY = "RustAmp Cortex-A9 rpmsg echo firmware for CPU1 of a Zynq-7000 (ax7015b), built from source"
DESCRIPTION = "Bare-metal firmware from RustAmp (examples/cortex-a9-rtu-echo) \
for the second Cortex-A9 core: Linux runs on CPU0 (maxcpus=1) and the a9-rproc \
remoteproc driver loads this firmware and starts it on CPU1. It announces \
rpmsg-tty, so rpmsg_tty creates /dev/ttyRPMSG0, and echoes everything written \
there. Installed to /lib/firmware/rustamp-a9-echo.elf."

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=482e63bde56d4bd2f7c028e38cfcf0ef"

require recipes-bsp/rustamp-common/rustamp-firmware.inc

# Linked to the carve-out at 0x3E000000 (top 32 MiB of the first GiB), which
# the board device tree reserves; built without debug UART (see the patch).
COMPATIBLE_MACHINE = "ax7015b"
A9_CARGO_FEATURES ?= "--features no-uart"

# Public repo, fetched anonymously over https. Pinned to main after "example:
# run the ZynqMP R5 firmware on AMD/Xilinx QEMU". The patches add the no-uart
# feature (the AX7015B's UART0 is neither routed nor clocked; to be upstreamed
# to RustAmp) and drop the loom model checker, whose cfg(loom) dependency
# otherwise pulls ~30 crates.io packages into an offline cargo resolve. What
# remains are the workspace's own path crates, so cargo builds offline.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://0001-cortex-a9-rtu-echo-no-uart-feature-for-boards-without-a-spare-UART.patch \
           file://0002-cortex-a9-rtu-echo-build-offline-without-the-loom-model-checker.patch \
"
SRCREV = "1acfdb0a43b12dd329da1525ccecfdd874c00f7a"

FW_NAME = "rustamp-a9-echo.elf"

do_compile() {
    rustamp_cargo_env
    export CARGO_TARGET_DIR="${B}"

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

# ptest (rustamp-firmware.inc): start the firmware through remoteproc, run
# checked echo round trips over /dev/ttyRPMSG0, then stop and restart it (the
# park-page reload) and echo again.
