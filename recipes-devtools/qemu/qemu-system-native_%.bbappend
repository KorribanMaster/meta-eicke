FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Cortex-M7 AMP boot for QEMU's imx8mp-evk (qemuimx8mp-evk machine): v2 of
# NXP's "hw/arm: Adding Cortex-M7 Asymmetric Multiprocessing boot support for
# i.MX8MP" series (GPC, GPR/CPUWAIT, SRC, MU, M7 clocks, the M7 core), backported
# from 11.1-dev to oe-core's QEMU 10.2, plus RustAmp's MU register-offset fix
# (without it Linux imx-mailbox kicks time out). The functional-test patch of
# the series is left out.
#
# Applied unconditionally: the patches only touch the imx8mp machine, and a
# machine override on a native recipe would break its shared sstate.
# Drop this once oe-core's QEMU contains the series (v4 or later).
SRC_URI += " \
    file://imx8mp-m7-amp/0001-hw-misc-Add-i.MX8MP-GPC-General-Power-Controller-IP.patch \
    file://imx8mp-m7-amp/0002-hw-misc-Add-GPR-General-Purpose-Register-IP-to-iMX8M.patch \
    file://imx8mp-m7-amp/0003-hw-misc-Add-SRC-System-Reset-Controller-to-i.MX8MP.patch \
    file://imx8mp-m7-amp/0004-hw-misc-Add-MU-Messaging-Unit-IP-to-i.MX8MP-device-m.patch \
    file://imx8mp-m7-amp/0005-hw-misc-Extend-i.MX8MP-CCM-with-Cortex-M7-clock-outp.patch \
    file://imx8mp-m7-amp/0006-hw-arm-Enable-Cortex-M7-AMP-boot-on-i.MX8MP.patch \
    file://imx8mp-m7-amp/0007-hw-misc-imx8mp_mu-compare-register-indices-not-byte-.patch \
    file://imx8mp-m7-amp/0008-hw-misc-imx8mp-use-the-QEMU-10.2-header-paths.patch \
"
