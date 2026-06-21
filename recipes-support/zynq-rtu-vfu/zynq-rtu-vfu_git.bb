SUMMARY = "Zynq RTU vfio-user device model (host QEMU test harness)"
DESCRIPTION = "Userspace vfio-user *server* that emulates the Zynq-7000 PCIe \
remoteproc endpoint (PCI 10ee:7015), so an eicke image can be booted under \
QEMU with '-device vfio-user-pci' and exercised against the zynq_pcie_rproc \
driver with no real board. This is a build-host (native) tool: QEMU is the \
vfio-user client, this server is the emulated endpoint."
HOMEPAGE = "https://github.com/KorribanMaster/openamp_pcie"
LICENSE = "GPL-2.0-only & BSD-3-Clause"
LIC_FILES_CHKSUM = " \
    file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6 \
    file://libvfio-user/LICENSE;md5=d29476ce8e14fc96682a158b2701c493 \
"

# The device model lives in sim/vfio-user of the (private) openamp_pcie repo and
# vendors libvfio-user as a submodule. We fetch only that submodule (not the
# repo's unrelated private FPGA/firmware submodules), placing it where the
# Makefile expects it (./libvfio-user under the sim dir).
SRC_URI = " \
    git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=feat/simulation;name=sim;destsuffix=${BP} \
    git://github.com/nutanix/libvfio-user.git;protocol=https;nobranch=1;name=lvfu;destsuffix=${BP}/sim/vfio-user/libvfio-user \
"
SRCREV_sim = "68528ce65ba402f11b9732ebe2a8cf3ab40e91bf"
SRCREV_lvfu = "f633a2cb28bc8f388d36530eada43c902419cfbf"
SRCREV_FORMAT = "sim_lvfu"

S = "${UNPACKDIR}/${BP}/sim/vfio-user"

# Build-host tool only.
inherit native

# libvfio-user builds with meson; its setup checks for json-c + cmocka.
# patchelf fixes the rpath so the installed server finds libvfio-user.so.
DEPENDS = "meson-native ninja-native pkgconfig-native json-c-native cmocka-native patchelf-native"

do_compile() {
    # The Makefile builds the vendored libvfio-user (meson) then links the
    # zynq_rtu_vfu server against it.
    oe_runmake
}

do_install() {
    install -d ${D}${bindir} ${D}${libdir}
    install -m 0755 ${S}/zynq_rtu_vfu ${D}${bindir}/zynq_rtu_vfu
    install -m 0644 ${S}/libvfio-user/build/lib/libvfio-user.so* ${D}${libdir}/
    # Make the server find its libvfio-user.so beside ${libdir}, relocation-proof.
    patchelf --set-rpath '$ORIGIN/../lib' ${D}${bindir}/zynq_rtu_vfu
}

# Run it with:  oe-run-native zynq-rtu-vfu-native zynq_rtu_vfu /tmp/sock
# or via meta-eicke/scripts/eicke-runsim.sh (boots an image under host QEMU).
