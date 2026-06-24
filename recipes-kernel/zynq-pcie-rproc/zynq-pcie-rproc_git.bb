SUMMARY = "Zynq-7000 PCIe remoteproc host driver (out-of-tree kernel module)"
DESCRIPTION = "Host-side Linux driver for a Zynq-7000 PCIe endpoint whose A9 \
firmware (the RTU) is loaded, started and messaged over the remoteproc/rpmsg \
stack through the PCIe BAR windows. Loads /lib/firmware/zynq_rtu.elf via \
request_firmware(). Requires CONFIG_REMOTEPROC + CONFIG_RPMSG_VIRTIO in the \
kernel (see the linux-yocto zynq-rproc.cfg fragment)."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module

# Private repo — fetched over ssh (uses the builder's key). For an https mirror
# switch to protocol=https. Pinned to a SRCREV on main; bump SRCREV to advance.
# Pinned to main after the merge of feat/rproc-stop-reload (cooperative stop +
# firmware reload via the OCM parker): .stop sets CTRL_HALT + rings the doorbell
# and polls STATUS_PARKED, so the host can reload a different RTU firmware with
# no power-cycle. Also includes the threaded-IRQ kick handler. Verified on the
# bench (start -> stop parks in OCM -> start reload, echo OK, PCIe link stayed up).
SRC_URI = "git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=main"
SRCREV = "7ba872628a877392f5de8674e7c07e6dfc02b36c"

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
