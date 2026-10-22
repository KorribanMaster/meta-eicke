# Shared by the RustAmp firmware recipes (rustamp-a9-echo, rustamp-m7-echo,
# rustamp-r5-echo): bare-metal firmware from the RustAmp repo, built with the
# prebuilt Rust toolchain, plus a ptest that loads it through remoteproc and
# echoes over rpmsg_tty. The recipe sets SRC_URI/SRCREV and its do_compile
# runs rustamp_cargo_env, then cargo build in its example.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

PV = "0.1+git"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# Build-only dep: the prebuilt rustc/cargo with the bare-metal ARM cores.
# Deliberately NOT 'inherit cargo' -- that drives OE's target rust for the
# Linux target triple.
DEPENDS = "rust-bin-thumbv7em-native"

# wrynose unpacks git SRC_URIs to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

inherit ptest

# A bare-metal blob, not code for the image's tune: must not be
# stripped/split or trigger an arch error. buildpaths: the release profile
# keeps debug info (remoteproc ignores it; handy for gdb over JTAG).
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} += "arch buildpaths"

RUST_BIN = "${STAGING_DATADIR_NATIVE}/rust-bin/bin"

# The environment for cargo with the prebuilt toolchain, in ${CARGO_HOME}.
rustamp_cargo_env() {
    # build.rs is compiled for the build host and needs a single-program
    # linker; BUILD_CC carries flags, so wrap it.
    printf '#!/bin/sh\nexec %s "$@"\n' "${BUILD_CC}" > ${B}/build-cc
    chmod +x ${B}/build-cc

    unset RUSTFLAGS RUSTC RUSTDOC CARGO_BUILD_TARGET CARGO_HOME
    export PATH="${RUST_BIN}:$PATH"
    export CARGO_HOME="${WORKDIR}/cargo-home"
    mkdir -p $CARGO_HOME
    host=$(rustc -vV | sed -n 's/^host: //p')
    [ -n "$host" ] || bbfatal "rustc reports no host triple"
    export "CARGO_TARGET_$(echo $host | tr 'a-z-' 'A-Z_')_LINKER=${B}/build-cc"
}

# ---------------------------------------------------------------------------
# ptest: run-ptest and rpmsg_echo_test.py come from the recipe, rpmsg_tty.py
# (raw tty helpers they share) from here. Enabled for just these recipes,
# like zynq-pcie-rproc, instead of distro-wide 'ptest'.
PTEST_ENABLED = "1"
SRC_URI:append = " file://run-ptest file://rpmsg_echo_test.py file://rpmsg_tty.py"
# python3-io carries termios (raw tty mode); select/fcntl and threading are
# in python3-core.
RDEPENDS:${PN}-ptest += "${PN} python3-core python3-io"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}
    install -m 0755 ${UNPACKDIR}/run-ptest ${D}${PTEST_PATH}/run-ptest
    install -m 0755 ${UNPACKDIR}/rpmsg_echo_test.py ${D}${PTEST_PATH}/rpmsg_echo_test.py
    install -m 0644 ${UNPACKDIR}/rpmsg_tty.py ${D}${PTEST_PATH}/rpmsg_tty.py
}
