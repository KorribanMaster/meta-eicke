SUMMARY = "RustAmp Cortex-M7 rpmsg echo firmware for i.MX 8M (QEMU imx8mp-evk, ccimx8mn-dvk), built from source"
DESCRIPTION = "Bare-metal Cortex-M7 firmware from RustAmp \
(examples/imx8mn-m7-echo) that Linux imx_rproc loads and starts through \
remoteproc. It announces rpmsg-tty, so rpmsg_tty creates /dev/ttyRPMSG0, and \
echoes everything written there. On silicon (ccimx8mn-dvk) it is the TCM \
build (code in ITCM, data in DTCM, MU interrupt); on QEMU imx8mp-evk it is \
built with --features qemu-imx8mp (code/data in DDR at 0x80000000, MU polling, \
UART4 log) for the Cortex-M7 AMP series. Installed to \
/lib/firmware/rustamp-echo.elf."

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=606a2f2b2d1dc89dfa532ffc2b825341"

PV = "0.1+git"

# The link layout differs per machine (cargo feature), so the ELF is
# machine-specific: the TCM build for real i.MX 8M silicon (no feature; vrings
# at 0x55000000 match the eicke-ccimx8mn-cm7 overlay), the DDR/MU-polling
# build for QEMU's imx8mp-evk.
COMPATIBLE_MACHINE = "qemuimx8mp-evk|ccimx8mn-dvk"
M7_CARGO_FEATURES ?= ""
M7_CARGO_FEATURES:qemuimx8mp-evk = "--features qemu-imx8mp"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# Build-only dep: the prebuilt rustc/cargo with thumbv7em core. Deliberately NOT
# 'inherit cargo' -- that drives OE's target rust for the Linux target triple.
DEPENDS = "rust-bin-thumbv7em-native"

# Public repo, fetched anonymously over https. The firmware's dependencies
# are the workspace's own path crates, so cargo builds offline with no
# crates.io fetch. Pinned to main after "example: run on QEMU
# imx8mp-evk with the Cortex-M7 AMP series". The patch moves the TCM build's
# resource table to DDR (0x550ff000, the overlay's rsc-table region): the A53
# cannot reach the TCMs while the M7 runs. To be upstreamed to RustAmp.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://run-ptest \
           file://rpmsg_echo_test.py \
           file://0001-imx8mn-m7-echo-keep-the-resource-table-in-DDR-on-silicon.patch \
"
SRCREV = "8ecace83b440a644ce0a4f20b2476dc2be06b415"

# wrynose unpacks git SRC_URIs to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

inherit ptest

# A bare-metal ARMv7-M blob, not code for the image's tune: must not be
# stripped/split or trigger an arch error. buildpaths: the release profile
# keeps debug info (remoteproc ignores it; handy for gdb on the M7).
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} += "arch buildpaths"

RUST_BIN = "${STAGING_DATADIR_NATIVE}/rust-bin/bin"
FW_NAME = "rustamp-echo.elf"

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

    # .cargo/config.toml in the example selects thumbv7em-none-eabihf and -Tlink.x.
    cd ${S}/examples/imx8mn-m7-echo
    cargo build --release --frozen ${M7_CARGO_FEATURES}
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware
    install -m 0644 ${B}/thumbv7em-none-eabihf/release/imx8mn-m7-echo \
        ${D}${nonarch_base_libdir}/firmware/${FW_NAME}
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/${FW_NAME}"

# ---------------------------------------------------------------------------
# ptest: start the firmware through remoteproc and run checked echo round trips
# over /dev/ttyRPMSG0 (the logic of RustAmp's QEMU /init, init.c). Enabled
# for just this recipe, like zynq-pcie-rproc, instead of distro-wide 'ptest'.
PTEST_ENABLED = "1"
# python3-io carries termios (raw tty mode); select/fcntl are in python3-core.
RDEPENDS:${PN}-ptest += "${PN} python3-core python3-io"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}
    install -m 0755 ${UNPACKDIR}/run-ptest ${D}${PTEST_PATH}/run-ptest
    install -m 0755 ${UNPACKDIR}/rpmsg_echo_test.py ${D}${PTEST_PATH}/rpmsg_echo_test.py
}
