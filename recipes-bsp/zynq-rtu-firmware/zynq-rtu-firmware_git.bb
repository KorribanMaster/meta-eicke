SUMMARY = "Zynq RTU remoteproc firmware (A9 OpenAMP rpmsg echo), built from source"
DESCRIPTION = "Cortex-A9 baremetal firmware ELF that the zynq-pcie-rproc host \
driver loads into the endpoint's DDR over PCIe via request_firmware(). Compiled \
from the openamp_pcie firmware/ CMake tree (the hello_world rpmsg-echo app) with \
the prebuilt arm-none-eabi toolchain — no Vitis. Installed to \
/lib/firmware/zynq_rtu.elf, the basename the driver requests (ZYNQ_RPROC_FW_NAME)."

# The app sources (rpmsg-echo) are OpenAMP/BSD; the statically linked BSP comes
# from Xilinx embeddedsw (MIT) and the OpenAMP libs (BSD). Kept as MIT to match
# the prior vendored-blob recipe; the firmware is a product artifact.
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PV = "1.0+git"

# Build-only deps: the baremetal cross toolchain (our native recipe) and CMake.
# Deliberately NOT 'inherit cmake' — that would force OE's x86 target toolchain;
# we drive cmake-native against the firmware's own arm-none-eabi toolchain file.
DEPENDS = "arm-gnu-toolchain-native cmake-native"

# Parent repo over ssh (private; builder's key), the three third-party submodules
# over https. NOT gitsm: that would also pull the repo's private doc/tutorials and
# the sim/libvfio-user submodules, which the firmware build doesn't need. Each
# submodule is unpacked beside the checkout and symlinked into firmware/third_party
# in do_configure (avoids unpack-order clobber of the parent's empty gitlink dirs).
# Submodules are pinned purely by SHA (nobranch=1): their gitlink commits are not
# always ancestors of the current upstream branch tips (e.g. embeddedsw's
# xilinx_v2025.2 has been rebased since), but the commits remain fetchable by SHA.
SRC_URI = "git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=main;name=main \
           git://github.com/Xilinx/embeddedsw.git;protocol=https;nobranch=1;destsuffix=${BP}-deps/embeddedsw;name=esw \
           git://github.com/OpenAMP/libmetal.git;protocol=https;nobranch=1;destsuffix=${BP}-deps/libmetal;name=metal \
           git://github.com/OpenAMP/open-amp.git;protocol=https;nobranch=1;destsuffix=${BP}-deps/open-amp;name=openamp \
"
SRCREV_main = "7ba872628a877392f5de8674e7c07e6dfc02b36c"
SRCREV_esw = "145cea8fcf98268c8b163f732c181f008e887e53"
SRCREV_metal = "80ab6b0d506a0d8eb4f2b87926682ababbb804b3"
SRCREV_openamp = "5bcc7c0401cace5e3d6719a06a0d1f92e30251aa"
SRCREV_FORMAT = "main_esw_metal_openamp"

# wrynose unpacks the un-suffixed git SRC_URI to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

# A target-arch-agnostic data blob for an ARM core — not host (x86) code, so it
# must not be split/stripped or trigger an arch sanity error.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
# arch: it's an ARM blob, not host code. buildpaths: the firmware is built -g3
# and the DWARF embeds the build dir; harmless (debug info isn't loaded by
# remoteproc) and useful for firmware debugging, so accept it for this blob.
INSANE_SKIP:${PN} += "arch buildpaths"

TOOLCHAIN_PREFIX = "${STAGING_DATADIR_NATIVE}/arm-gnu-toolchain/bin/arm-none-eabi-"

do_configure() {
    # Drop the parent's empty submodule placeholders and link the real trees in.
    for sm in embeddedsw libmetal open-amp; do
        rm -rf ${S}/firmware/third_party/${sm}
        ln -sfn ${UNPACKDIR}/${BP}-deps/${sm} ${S}/firmware/third_party/${sm}
    done

    cmake -S ${S}/firmware -B ${B} \
        -DCMAKE_TOOLCHAIN_FILE=${S}/firmware/toolchain-arm-none-eabi-a9.cmake \
        -DCROSS_PREFIX=${TOOLCHAIN_PREFIX}
}

do_compile() {
    # Only the RTU app; it doesn't depend on the FSBL / ps7_init / .xsa.
    cmake --build ${B} --target hello_world -j ${@oe.utils.cpu_count()}
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware
    install -m 0644 ${B}/hello_world/hello_world.elf ${D}${nonarch_base_libdir}/firmware/zynq_rtu.elf
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/zynq_rtu.elf"
