SUMMARY = "ptest suite for the Zynq PCIe remoteproc/rpmsg RTU driver"
DESCRIPTION = "Dependency-free Python tests (run_tests.py + rpmsg_lib.py) that \
drive /sys/class/remoteproc and the rpmsg_char ABI to exercise the \
zynq_pcie_rproc driver against the RTU endpoint (a real board or the \
sim/vfio-user device model). Packaged as a Yocto ptest."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

# Same repo as the driver; feat/simulation carries the run-ptest entry point.
SRC_URI = "git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=feat/simulation"
SRCREV = "3e2699d8e0e3ed87217a7918d1e618f219a2fd35"
S = "${UNPACKDIR}/git"

inherit ptest

# The ptest is the entire content of this recipe (no main package payload).
ALLOW_EMPTY:${PN} = "1"

# Python stdlib only (fcntl, mmap, struct, select, glob); plus the driver +
# firmware the tests exercise.
RDEPENDS:${PN}-ptest += " \
    python3-core \
    python3-fcntl \
    python3-mmap \
    kernel-module-zynq-pcie-rproc \
    zynq-rtu-firmware \
"

do_install() {
    :
}

do_install_ptest() {
    install -m 0755 ${S}/driver/test/run-ptest       ${D}${PTEST_PATH}/run-ptest
    install -m 0755 ${S}/driver/test/run_tests.py    ${D}${PTEST_PATH}/run_tests.py
    install -m 0644 ${S}/driver/test/rpmsg_lib.py    ${D}${PTEST_PATH}/rpmsg_lib.py
    install -m 0755 ${S}/driver/test/echo_test.py    ${D}${PTEST_PATH}/echo_test.py
    install -m 0755 ${S}/driver/test/inspect_bar1.py ${D}${PTEST_PATH}/inspect_bar1.py
}
