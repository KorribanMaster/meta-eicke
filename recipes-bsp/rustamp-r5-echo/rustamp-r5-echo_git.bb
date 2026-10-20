SUMMARY = "RustAmp Cortex-R5F rpmsg echo firmware for both R5s of a Zynq UltraScale+ (te0820), built from source"
DESCRIPTION = "Bare-metal firmware from RustAmp (examples/zynqmp-r5-echo) for \
the two Cortex-R5F cores of the RPU in split mode: Linux's xlnx_r5_remoteproc \
loads it into a core's TCMs and its carve-out and starts it. Each core \
announces rpmsg-tty, so rpmsg_tty creates a /dev/ttyRPMSG<n> per core, and \
echoes everything written there. Built twice, for R5_0 and (feature r5-1) \
R5_1, and installed to /lib/firmware/rustamp-r5_0-echo.elf and \
rustamp-r5_1-echo.elf."

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=482e63bde56d4bd2f7c028e38cfcf0ef"

PV = "0.1+git"

# Linked to the te0820 device tree's carve-outs (rproc_N_fw_image and the
# vrings/buffers after them); logs only to the remoteproc trace buffer, as
# the board has no spare UART (see the patches).
COMPATIBLE_MACHINE = "te0820"
R5_CARGO_FEATURES ?= "no-uart"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# Build-only dep: the prebuilt rustc/cargo with the armv7r-none-eabihf core.
# Deliberately NOT 'inherit cargo' -- that drives OE's target rust for the
# Linux target triple.
DEPENDS = "rust-bin-thumbv7em-native"

# Public repo, fetched anonymously over https, at the same revision as
# rustamp-a9-echo. The patches: 0001 drops the loom model checker from the
# shared crates (as for the A9 firmware), 0002 trims the example's lockfile
# accordingly, 0003 adds the no-uart feature, 0004 the r5-1 feature (to be
# upstreamed to RustAmp). The example's crates.io dependencies (the aarch32
# runtime and what it needs) are fetched below and vendored for an offline
# --frozen build.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://0001-cortex-a9-rtu-echo-build-offline-without-the-loom-mo.patch \
           file://0002-zynqmp-r5-echo-drop-the-loom-packages-from-the-lockf.patch \
           file://0003-zynqmp-r5-echo-no-uart-feature-for-boards-without-a-.patch \
           file://0004-zynqmp-r5-echo-r5-1-feature-to-run-on-R5_1.patch \
           file://run-ptest \
           file://rpmsg_echo_test.py \
           file://rpmsg_bench.py \
"
SRCREV = "1acfdb0a43b12dd329da1525ccecfdd874c00f7a"

# examples/zynqmp-r5-echo/Cargo.lock after 0002 (sha256 = the lockfile's
# checksums).
SRC_URI += " \
    crate://crates.io/aarch32-cpu/0.4.0 \
    crate://crates.io/aarch32-rt/0.5.0 \
    crate://crates.io/aarch32-rt-macros/0.5.0 \
    crate://crates.io/arbitrary-int/2.2.0 \
    crate://crates.io/arm-targets/0.4.3 \
    crate://crates.io/bitbybit/2.0.1 \
    crate://crates.io/critical-section/1.2.0 \
    crate://crates.io/num_enum/0.7.6 \
    crate://crates.io/num_enum_derive/0.7.6 \
    crate://crates.io/proc-macro2/1.0.107 \
    crate://crates.io/quote/1.0.47 \
    crate://crates.io/rustversion/1.0.23 \
    crate://crates.io/syn/2.0.119 \
    crate://crates.io/syn/3.0.9 \
    crate://crates.io/thiserror/2.0.21 \
    crate://crates.io/thiserror-impl/2.0.21 \
    crate://crates.io/unicode-ident/1.0.27 \
"
SRC_URI[aarch32-cpu-0.4.0.sha256sum] = "685b9d999a309934c295fce81bb0ab632351449ba87990588cef879521c8f2aa"
SRC_URI[aarch32-rt-0.5.0.sha256sum] = "5a44a30d275994d1fad7f3097d1e9f9866e8b61bafa04a2364e0289987b04d8c"
SRC_URI[aarch32-rt-macros-0.5.0.sha256sum] = "1b4120ca224ada4422e93538adb8ad05f643d386b25e278379f81cdf3c2cdca1"
SRC_URI[arbitrary-int-2.2.0.sha256sum] = "0ab387a0c4143b4fd3804e193946e78c8ab16cf26c5fde2048e4ac3d618c3b64"
SRC_URI[arm-targets-0.4.3.sha256sum] = "c0555f30eb9cd1a07d7677d01971da6c74cbbb0f73f0667cbee74e590a68a619"
SRC_URI[bitbybit-2.0.1.sha256sum] = "bf218b839af0730012cc21856c4149789afde9ae6dc3078c7adaf311e8b76854"
SRC_URI[critical-section-1.2.0.sha256sum] = "790eea4361631c5e7d22598ecd5723ff611904e3344ce8720784c93e3d83d40b"
SRC_URI[num_enum-0.7.6.sha256sum] = "5d0bca838442ec211fa11de3a8b0e0e8f3a4522575b5c4c06ed722e005036f26"
SRC_URI[num_enum_derive-0.7.6.sha256sum] = "680998035259dcfcafe653688bf2aa6d3e2dc05e98be6ab46afb089dc84f1df8"
SRC_URI[proc-macro2-1.0.107.sha256sum] = "985e7ec9bb745e6ce6535b544d84d6cd6f7ad8bd711c398938ae983b91a766d9"
SRC_URI[quote-1.0.47.sha256sum] = "1fbf4db142a473a8d80c26bbf18454ed458bf8d26c8219c331daecfdbd079001"
SRC_URI[rustversion-1.0.23.sha256sum] = "cf54715a573b99ac80df0bc206da022bcd442c974952c7b9720069370852e21f"
SRC_URI[syn-2.0.119.sha256sum] = "872831b642d1a07999a962a351ed35b955ea2cfc8f3862091e2a240a84f17297"
SRC_URI[syn-3.0.9.sha256sum] = "d78c8dee4c7bf0e14673097256fed6142ce9d3b85a408189d07482442145823b"
SRC_URI[thiserror-2.0.21.sha256sum] = "09e52cb86a36cede5cb101bf8908837b3e4c6e5e59fe7fd85c23fb56200d189e"
SRC_URI[thiserror-impl-2.0.21.sha256sum] = "fe5197923287db20a58125f0bc85c062f7f2c892de97b18c356f9efb14b28524"
SRC_URI[unicode-ident-1.0.27.sha256sum] = "a2c754d6c33795a1c324727428e5a7dedb5b06195f9890bdbcba760d3e246563"

# wrynose unpacks git SRC_URIs to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

inherit ptest

# Bare-metal ARMv7-R blobs, not code for the image's tune: must not be
# stripped/split or trigger an arch error. buildpaths: the release profile
# keeps debug info (remoteproc ignores it; handy for gdb over JTAG).
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} += "arch buildpaths"

RUST_BIN = "${STAGING_DATADIR_NATIVE}/rust-bin/bin"
# The crate fetcher unpacks the crates (with their .cargo-checksum.json)
# here; cargo_common.bbclass's directory source, without the class.
CRATES_DIR = "${UNPACKDIR}/cargo_home/bitbake"

do_compile() {
    # build.rs is compiled for the build host and needs a single-program linker;
    # BUILD_CC carries flags, so wrap it.
    printf '#!/bin/sh\nexec %s "$@"\n' "${BUILD_CC}" > ${B}/build-cc
    chmod +x ${B}/build-cc

    unset RUSTFLAGS RUSTC RUSTDOC CARGO_BUILD_TARGET CARGO_HOME
    export PATH="${RUST_BIN}:$PATH"
    export CARGO_HOME="${WORKDIR}/cargo-home"
    export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER="${B}/build-cc"
    mkdir -p ${CARGO_HOME}
    cat > ${CARGO_HOME}/config.toml <<EOF
[source.bitbake]
directory = "${CRATES_DIR}"

[source.crates-io]
replace-with = "bitbake"
EOF

    # .cargo/config.toml in the example selects armv7r-none-eabihf and
    # -Tlink.x. One target dir per core: build.rs writes a different
    # memory.x for each.
    cd ${S}/examples/zynqmp-r5-echo
    CARGO_TARGET_DIR="${B}/r5_0" cargo build --release --frozen \
        --features "${R5_CARGO_FEATURES}"
    CARGO_TARGET_DIR="${B}/r5_1" cargo build --release --frozen \
        --features "${R5_CARGO_FEATURES} r5-1"
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware
    for core in r5_0 r5_1; do
        install -m 0644 ${B}/$core/armv7r-none-eabihf/release/zynqmp-r5-echo \
            ${D}${nonarch_base_libdir}/firmware/rustamp-$core-echo.elf
    done
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/rustamp-r5_*-echo.elf"

# ---------------------------------------------------------------------------
# ptest: start the firmware on both R5s through remoteproc, run checked echo
# round trips on both /dev/ttyRPMSG<n> at the same time, then stop and
# restart both and echo again; then rpmsg_bench.py measures round-trip
# latency and throughput, per core and both at once. Enabled for just this recipe, like
# rustamp-a9-echo.
PTEST_ENABLED = "1"
# python3-io carries termios (raw tty mode); select/fcntl are in
# python3-core, threading too.
RDEPENDS:${PN}-ptest += "${PN} python3-core python3-io"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}
    install -m 0755 ${UNPACKDIR}/run-ptest ${D}${PTEST_PATH}/run-ptest
    install -m 0755 ${UNPACKDIR}/rpmsg_echo_test.py ${D}${PTEST_PATH}/rpmsg_echo_test.py
    install -m 0755 ${UNPACKDIR}/rpmsg_bench.py ${D}${PTEST_PATH}/rpmsg_bench.py
}
