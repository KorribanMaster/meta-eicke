# Enable the remoteproc / rpmsg / virtio stack the Zynq PCIe host driver
# (zynq-pcie-rproc) depends on. linux-yocto inherits kernel-yocto, which
# auto-merges any *.cfg listed in SRC_URI as a configuration fragment.
FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://zynq-rproc.cfg"
