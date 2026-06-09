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
# switch to protocol=https. The driver currently lives on the feature branch;
# repoint branch/SRCREV to main once merged.
SRC_URI = "git://git@github.com/KorribanMaster/openamp_pcie.git;protocol=ssh;branch=feat/host-rproc-driver"
SRCREV = "e020a51297d0b254ab8cd9970fa555767bb1b7fc"

# Kbuild (obj-m) lives in the driver/ subdir of the repo.
S = "${WORKDIR}/git/driver"

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

# Auto-load on boot (PCIe probe binds the endpoint).
KERNEL_MODULE_AUTOLOAD += "zynq_pcie_rproc"

RPROVIDES:${PN} += "kernel-module-zynq-pcie-rproc"
