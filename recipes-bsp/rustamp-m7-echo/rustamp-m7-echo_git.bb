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

require recipes-bsp/rustamp-common/rustamp-firmware.inc

# The link layout differs per machine (cargo feature), so the ELF is
# machine-specific: the TCM build for real i.MX 8M silicon (no feature; vrings
# at 0x55000000 match the eicke-ccimx8mn-cm7 overlay), the DDR/MU-polling
# build for QEMU's imx8mp-evk.
COMPATIBLE_MACHINE = "qemuimx8mp-evk|ccimx8mn-dvk"
M7_CARGO_FEATURES ?= ""
M7_CARGO_FEATURES:qemuimx8mp-evk = "--features qemu-imx8mp"

# Public repo, fetched anonymously over https. The firmware's dependencies
# are the workspace's own path crates, so cargo builds offline with no
# crates.io fetch. Pinned to main after "example: run on QEMU
# imx8mp-evk with the Cortex-M7 AMP series". The patch moves the TCM build's
# resource table to DDR (0x550ff000, the overlay's rsc-table region): the A53
# cannot reach the TCMs while the M7 runs. To be upstreamed to RustAmp.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://0001-imx8mn-m7-echo-keep-the-resource-table-in-DDR-on-silicon.patch \
"
SRCREV = "8ecace83b440a644ce0a4f20b2476dc2be06b415"

FW_NAME = "rustamp-echo.elf"

do_compile() {
    rustamp_cargo_env
    export CARGO_TARGET_DIR="${B}"

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

# ptest (rustamp-firmware.inc): start the firmware through remoteproc and run
# checked echo round trips over /dev/ttyRPMSG0 (the logic of RustAmp's QEMU
# /init, init.c).
