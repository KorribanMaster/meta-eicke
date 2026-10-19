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

# qemuimx8mp-evk: i.MX 8M Plus drivers on top of the qemuarm64 BSP, and the
# EVK device tree with the Cortex-M7 remoteproc node (from RustAmp). The
# .dts is not in the kernel tree, so copy it next to imx8mp-evk.dts; kbuild
# builds freescale/<name>.dtb without a Makefile entry.
SRC_URI:append:qemuimx8mp-evk = " \
    file://imx8mp-qemu.cfg \
    file://imx8mp-evk-rpmsg-qemu.dts \
"

do_configure:append:qemuimx8mp-evk() {
    install -m 0644 ${UNPACKDIR}/imx8mp-evk-rpmsg-qemu.dts ${S}/arch/arm64/boot/dts/freescale/
}

# Fail the build if any =y of imx8mp-qemu.cfg did not make it into .config.
# Kconfig silently drops a symbol whose parent menuconfig is off (ARCH_NXP,
# PINCTRL, REGULATOR all bit us) and kernel_configcheck did not report it;
# the result was a kernel that boots without console or root device.
# Registered only for qemuimx8mp-evk, so the other machines' kernel signatures
# (and their sstate) stay untouched.
do_eicke_check_fragment() {
    missing=""
    for opt in $(grep -E '^CONFIG_[A-Z0-9_]+=y' ${UNPACKDIR}/imx8mp-qemu.cfg); do
        grep -qx "$opt" ${B}/.config || missing="$missing ${opt%=y}"
    done
    [ -z "$missing" ] || bbfatal "imx8mp-qemu.cfg options missing from .config (parent menu off?):$missing"
}
python () {
    if 'qemuimx8mp-evk' in d.getVar('OVERRIDES').split(':'):
        bb.build.addtask('do_eicke_check_fragment', 'do_compile', 'do_kernel_configme', d)
}

# NOTE (learned the hard way): user SRC_URI fragments are merged BEFORE the
# kernel-cache feature fragments, so a =y here loses to a later feature =m
# (e.g. features/nf_tables/nf_tables.cfg). The nftables pieces the prod
# firewall needs therefore stay =m and are shipped as signed module packages
# via eicke-firewall's RDEPENDS instead of a fragment. Only set options in
# fragments that no kernel-cache feature redefines (verify with
# `bitbake linux-yocto -c kernel_configcheck` + a grep of the final .config).
