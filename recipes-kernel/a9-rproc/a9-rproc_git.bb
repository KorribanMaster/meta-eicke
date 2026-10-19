SUMMARY = "RustAmp a9-rproc: a spare Cortex-A9 core as a Linux remote processor (out-of-tree module)"
DESCRIPTION = "remoteproc driver from RustAmp (linux/a9-rproc) that loads \
firmware onto CPU1 of a Zynq-7000 booted with maxcpus=1 and starts it through \
the boot ROM's release address (0xfffffff0). SGIs notify in both directions; \
stop/start reloads go through the firmware's park page. Binds to \
compatible = \"rustamp,cortex-a9-rproc\" and loads automatically from it."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module

COMPATIBLE_MACHINE = "ax7015b"

# Same repo and revision as rustamp-a9-echo, so driver and firmware agree on
# the park-page protocol. The patch adds the modules_install target
# module.bbclass runs: kbuild's install also signs the module, which the
# hardened kernel (MODULE_SIG_FORCE) requires. To be upstreamed to RustAmp.
SRC_URI = "git://github.com/KorribanMaster/rustamp.git;protocol=https;branch=main \
           file://0001-a9-rproc-add-a-modules_install-target.patch;patchdir=${UNPACKDIR}/${BP} \
"
SRCREV = "1acfdb0a43b12dd329da1525ccecfdd874c00f7a"

# wrynose unpacks git SRC_URIs to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}/linux/a9-rproc"

# The Makefile selects the kernel tree via `KDIR ?= /lib/modules/...`; point it
# at the staged target kernel (as zynq-pcie-rproc does).
EXTRA_OEMAKE:append = " KDIR=${STAGING_KERNEL_DIR}"

# remoteproc, rpmsg_virtio and rpmsg_tty are built in (=y; amd-zynq BSP and
# ax7015b.cfg), so there are no kernel-module-* packages to RDEPEND on.
RPROVIDES:${PN} += "kernel-module-a9-rproc"
