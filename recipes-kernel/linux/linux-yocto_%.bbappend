# Enable the remoteproc / rpmsg / virtio stack the Zynq PCIe host driver
# (zynq-pcie-rproc) depends on. linux-yocto inherits kernel-yocto, which
# auto-merges any *.cfg listed in SRC_URI as a configuration fragment.
FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://zynq-rproc.cfg"

# Overlay filesystem support, needed by the prod image's overlayfs-etc feature
# (persistent /etc overlay on the data partition).
SRC_URI += "file://overlayfs.cfg"

# Security hardening. The kernel is per-machine and shared between dev and
# prod images -- hardening applies to both on purpose (one tested kernel,
# dev/prod parity; lockdown "integrity" mode keeps tracing/perf usable for
# dev). Per-option rationale lives in the fragments; overview in
# doc/security.md. Deliberately NOT kernel-cache's
# features/security/security.scc, which invisibly bundles unrelated changes
# (INET_DIAG=n breaks `ss`, MODIFY_LDT_SYSCALL=n, EXPERT=y).
SRC_URI += "file://hardening.cfg"
SRC_URI:append:x86-64 = " file://hardening-x86.cfg"

# NOTE (learned the hard way): user SRC_URI fragments are merged BEFORE the
# kernel-cache feature fragments, so a =y here loses to a later feature =m
# (e.g. features/nf_tables/nf_tables.cfg). The nftables pieces the prod
# firewall needs therefore stay =m and are shipped as signed module packages
# via eicke-firewall's RDEPENDS instead of a fragment. Only set options in
# fragments that no kernel-cache feature redefines (verify with
# `bitbake linux-yocto -c kernel_configcheck` + a grep of the final .config).
