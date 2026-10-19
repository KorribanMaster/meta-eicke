SUMMARY = "Prebuilt Rust toolchain (rustc + cargo) with bare-metal ARM core/alloc"
DESCRIPTION = "Official prebuilt rustc/cargo from static.rust-lang.org plus the \
x86_64 host std (for build.rs) and the thumbv7em-none-eabihf and \
armv7a-none-eabi core/alloc, staged into the native sysroot as a build-only \
toolchain. Used to compile bare-metal Cortex-M7 firmware (rustamp-m7-echo) and \
Cortex-A9 firmware (rustamp-a9-echo): OE's rust-native only builds std for \
the host and targets OE knows, not bare-metal no_std targets. Never shipped in \
any image."

# Build-only native tool (prebuilt third-party binaries, not rebuilt here). The
# real licence is MIT OR Apache-2.0; marked CLOSED like arm-gnu-toolchain-native
# because nothing from it lands in a target package.
LICENSE = "CLOSED"

inherit native
INHIBIT_DEFAULT_DEPS = "1"

# Same version as oe-core's rust, so the two never disagree on edition/MSRV.
PV = "1.94.1"
HOST_TRIPLE = "x86_64-unknown-linux-gnu"
RUST_COMPONENTS = " \
    rustc-${PV}-${HOST_TRIPLE} \
    cargo-${PV}-${HOST_TRIPLE} \
    rust-std-${PV}-${HOST_TRIPLE} \
    rust-std-${PV}-thumbv7em-none-eabihf \
    rust-std-${PV}-armv7a-none-eabi \
"
SRC_URI = " \
    https://static.rust-lang.org/dist/rustc-${PV}-${HOST_TRIPLE}.tar.xz;name=rustc \
    https://static.rust-lang.org/dist/cargo-${PV}-${HOST_TRIPLE}.tar.xz;name=cargo \
    https://static.rust-lang.org/dist/rust-std-${PV}-${HOST_TRIPLE}.tar.xz;name=std-host \
    https://static.rust-lang.org/dist/rust-std-${PV}-thumbv7em-none-eabihf.tar.xz;name=std-thumbv7em \
    https://static.rust-lang.org/dist/rust-std-${PV}-armv7a-none-eabi.tar.xz;name=std-armv7a \
"
SRC_URI[rustc.sha256sum] = "0a6b16ca476461c6238f48efcb74a7aa08a3983e61adf587f208ba48504d87e1"
SRC_URI[cargo.sha256sum] = "03595624775204f80ff3a67986f69c8918e4cd311877ced635108888013a1e39"
SRC_URI[std-host.sha256sum] = "74109ee2c19098f1d7122087dd338a8f643203b3ce678c9d58169db733e17a03"
SRC_URI[std-thumbv7em.sha256sum] = "b8ad781fd218c186773e8d7693d10a63288d04947d0119e4ed9e75336e568997"
SRC_URI[std-armv7a.sha256sum] = "aca338cb24a2b8032d698bc8c65acd2385a04783de51bf5d14861c81d73f50b2"

S = "${UNPACKDIR}"

# Prebuilt x86_64 host binaries + bare-metal ARM rlibs: don't strip or run the
# usual ELF/arch/rpath QA against them.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP:${PN} = "already-stripped arch staticdev libdir rpaths file-rdeps textrel ldflags"
EXCLUDE_FROM_SHLIBS = "1"

# Consumers reference the toolchain by absolute path from the native sysroot:
#   ${STAGING_DATADIR_NATIVE}/rust-bin/bin/{rustc,cargo}
# rustc locates its sysroot (lib/rustlib) relative to its own binary, so the
# tree is relocatable.
TC_DESTDIR = "${datadir}/rust-bin"

do_install() {
    for c in ${RUST_COMPONENTS}; do
        ${UNPACKDIR}/$c/install.sh --prefix=${D}${TC_DESTDIR} \
            --disable-ldconfig --without=rust-docs
    done
    # install.sh records the absolute ${D} paths for its uninstaller; useless
    # (and a buildpaths leak) in a staged tree.
    rm -f ${D}${TC_DESTDIR}/lib/rustlib/uninstall.sh \
          ${D}${TC_DESTDIR}/lib/rustlib/install.log \
          ${D}${TC_DESTDIR}/lib/rustlib/manifest-*
}

# Stage the whole tree into the native sysroot (do_populate_sysroot picks up
# everything under ${D}).
SYSROOT_DIRS += "${TC_DESTDIR}"
