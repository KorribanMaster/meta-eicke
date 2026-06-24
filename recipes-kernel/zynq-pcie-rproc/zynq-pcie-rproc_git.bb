SUMMARY = "Zynq-7000 PCIe remoteproc host driver (out-of-tree kernel module)"
DESCRIPTION = "Host-side Linux driver for a Zynq-7000 PCIe endpoint whose A9 \
firmware (the RTU) is loaded, started and messaged over the remoteproc/rpmsg \
stack through the PCIe BAR windows. Loads /lib/firmware/zynq_rtu.elf via \
request_firmware(). Requires CONFIG_REMOTEPROC + CONFIG_RPMSG_VIRTIO in the \
kernel (see the linux-yocto zynq-rproc.cfg fragment)."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module ptest

# Private repo — fetched over ssh (uses the builder's key). For an https mirror
# switch to protocol=https. Pinned to a SRCREV on main; bump SRCREV to advance.
# Pinned to main after the merge of test/driver-pytest: a pytest suite covering
# all driver functions (remoteproc lifecycle, rpmsg echo, Kick-IP registers,
# sysfs, fault/auto-recovery) shipped as a Yocto ptest, on top of the RTU
# fault-handling work (decoded STATUS logging + rtu_status sysfs, watchdog
# fault/hang detection, crash-loop-guarded auto-recovery via the OCM parker,
# firmware trace0). Verified on the bench.
SRC_URI = "git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=main"
SRCREV = "27374d3e0fb28596fe462e0d7445ede42ccc05ee"

# Kbuild (obj-m) lives in the driver/ subdir of the repo. wrynose unpacks git
# SRC_URIs to ${UNPACKDIR}/${BP} (BB_GIT_DEFAULT_DESTSUFFIX = "${BP}"), not /git.
S = "${UNPACKDIR}/${BP}/driver"

# The driver Makefile selects the kernel tree via `KDIR ?= /lib/modules/$(uname
# -r)/build` (a host build), whereas module.bbclass exports KERNEL_SRC/
# KERNEL_PATH. Point KDIR at the staged *target* kernel so the module
# cross-builds against the Yocto kernel (a make command-line assignment
# overrides the Makefile's ?= default).
EXTRA_OEMAKE:append = " KDIR=${STAGING_KERNEL_DIR}"

# The remoteproc/rpmsg/virtio symbols this module binds to are built into the
# kernel image (=y) via the linux-yocto zynq-rproc.cfg fragment, so there are no
# kernel-module-* packages to RDEPEND on. If you switch those symbols to =m in
# the fragment, add the matching kernel-module-* RDEPENDS here.

# NOT auto-loaded at boot. The driver's probe waits for the endpoint FSBL/DDR/PL
# to come up and times out (~10s, -110) if the Zynq PL isn't loaded yet — which
# at boot it usually isn't — stalling systemd-modules-load on the critical path.
# Load it on demand once the PL is up:  modprobe zynq_pcie_rproc
# (To restore boot autoload, re-add: KERNEL_MODULE_AUTOLOAD += "zynq_pcie_rproc")

RPROVIDES:${PN} += "kernel-module-zynq-pcie-rproc"

# ---------------------------------------------------------------------------
# ptest: the repo's driver/test/ pytest suite (run-ptest entry point), run on
# the host the Zynq board is plugged into. Enable the ptest package for just
# this recipe (PTEST_ENABLED=1) rather than adding 'ptest' to DISTRO_FEATURES
# distro-wide (which would rebuild many recipes' ptests). With no RTU endpoint
# the tests report SKIP (never FAIL), so the suite is safe on any host.
PTEST_ENABLED = "1"

# The suite is pure Python (stdlib + pytest); the firmware is what it boots.
RDEPENDS:${PN}-ptest += "${PN} zynq-rtu-firmware ptest-runner \
    python3-core python3-pytest python3-fcntl python3-mmap"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}
    # Ship the whole test dir (conftest.py, rpmsg_lib.py, test_*.py, run-ptest,
    # the dependency-free run_tests.py fallback, README). kicktool is the C
    # equivalent and is packaged separately (see below) as /usr/bin/kicktool.
    cp -r ${S}/test/. ${D}${PTEST_PATH}/
    chmod 0755 ${D}${PTEST_PATH}/run-ptest
}

# ---------------------------------------------------------------------------
# kicktool: a no-Python C helper (BAR0 Kick-IP register dump + rpmsg echo /
# fault-injection) for minimal rootfs debugging. Built for the target with the
# OE toolchain (libc only) and shipped as its own package so it can be added to
# the dev image without pulling it into the lean base/prod images.
PACKAGES =+ "${PN}-kicktool"
FILES:${PN}-kicktool = "${bindir}/kicktool"
RDEPENDS:${PN}-kicktool += "${PN}"

do_compile:append() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o ${B}/kicktool ${S}/test/kicktool.c
}

do_install:append() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/kicktool ${D}${bindir}/kicktool
}
