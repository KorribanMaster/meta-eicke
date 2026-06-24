SUMMARY = "ARM GNU Toolchain (arm-none-eabi) — prebuilt baremetal cross GCC + newlib"
DESCRIPTION = "Vendored prebuilt arm-none-eabi GCC/newlib from ARM, staged into \
the native sysroot for use as a build-only cross toolchain. Used to compile the \
Zynq-7000 Cortex-A9 baremetal RTU firmware (zynq-rtu-firmware) with the upstream \
CMake build, which expects a plain arm-none-eabi- toolchain on disk (selected via \
CROSS_PREFIX). The toolchain is never shipped in any image."

# Build-only native tool (a prebuilt third-party blob, not rebuilt here). The
# real licence is GPL-3.0-with-GCC-exception (GCC) + newlib's BSD-style terms;
# marked CLOSED because nothing from it lands in a target package and we don't
# vendor the licence text.
LICENSE = "CLOSED"

inherit native
INHIBIT_DEFAULT_DEPS = "1"

PV = "14.2.rel1"
TARBALL = "arm-gnu-toolchain-${PV}-x86_64-arm-none-eabi.tar.xz"
SRC_URI = "https://developer.arm.com/-/media/Files/downloads/gnu/${PV}/binrel/${TARBALL};downloadfilename=${TARBALL}"
SRC_URI[sha256sum] = "62a63b981fe391a9cbad7ef51b17e49aeaa3e7b0d029b36ca1e9c3b2a9b78823"

# bitbake auto-extracts the .tar.xz to this top-level dir (under UNPACKDIR).
S = "${UNPACKDIR}/arm-gnu-toolchain-${PV}-x86_64-arm-none-eabi"

# Prebuilt x86_64 host binaries + bundled arm-none-eabi target libs: don't strip
# or run the usual ELF/arch/rpath QA against them.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} = "already-stripped arch staticdev libdir rpaths file-rdeps textrel ldflags"
EXCLUDE_FROM_SHLIBS = "1"

# Consumers reference the toolchain by absolute path from the native sysroot:
#   ${STAGING_DATADIR_NATIVE}/arm-gnu-toolchain/bin/arm-none-eabi-
TC_DESTDIR = "${datadir}/arm-gnu-toolchain"

do_install() {
    install -d ${D}${TC_DESTDIR}
    cp -a ${S}/. ${D}${TC_DESTDIR}/
}

# Stage the whole tree into the native sysroot (do_populate_sysroot picks up
# everything under ${D}).
SYSROOT_DIRS += "${TC_DESTDIR}"
