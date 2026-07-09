SUMMARY = "iPXE network bootloader (x86_64 UEFI application)"
DESCRIPTION = "Builds the iPXE UEFI application with an embedded retry/autoboot \
script. The eicke netboot image places three copies of it on the ESP: the \
firmware fallback path (EFI/BOOT/BOOTX64.EFI, used on first boot before the \
UEFI A/B boot entries exist) and the two update slots ipxe-a.efi/ipxe-b.efi \
managed by eicke-ipxeconfirm + the eicke-update-image-netboot SWUpdate bundle."
HOMEPAGE = "https://ipxe.org"
# Most of iPXE is GPLv2-or-later; parts carry the UBDL relicensing permission.
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=92be9bced83819c46c5ab272173c4aa7 \
                    file://COPYING.GPLv2;md5=b234ee4d69f5fce4486a80fdaf4a4263"

SRC_URI = "git://github.com/ipxe/ipxe.git;protocol=https;branch=master \
           file://embed.ipxe"
SRCREV = "433a8f552f007255d3a0fa13aa7963e0281c9981"
PV = "1.21.1+git"

# wrynose unpacks the un-suffixed git SRC_URI to ${UNPACKDIR}/${BP}.
S = "${UNPACKDIR}/${BP}"

# iPXE's build runs helper scripts (parserom.pl et al.) with perl.
DEPENDS = "perl-native"
COMPATIBLE_HOST = "x86_64.*-linux"

# user-key-store (meta-signing-key) provides uefi_sb_sign(); inert in non-SB
# builds (its class-target DEPENDS only add openssl-native there).
inherit deploy user-key-store

# CROSS is iPXE's toolchain-prefix knob (host utilities use HOST_CC=gcc from
# iPXE's Makefile; gcc is in bitbake's hosttools). The embedded script is
# baked into the binary.
EXTRA_OEMAKE = "NO_WERROR=1 V=1 CROSS=${HOST_PREFIX} EMBED=${UNPACKDIR}/embed.ipxe"

do_configure[noexec] = "1"

do_compile() {
    # iPXE is freestanding and owns its compiler flags (the Makefile does
    # CFLAGS +=): bitbake's exported CFLAGS/CPPFLAGS/LDFLAGS would leak in and
    # break the build, but passing CFLAGS= on the make command line would
    # *replace* iPXE's flags. Clear the environment instead.
    unset CFLAGS CPPFLAGS LDFLAGS
    # iPXE's BUILD_ID_CMD pipes through cksum, which is not in bitbake's
    # restricted hosttools PATH and silently yields an empty --defsym (ld
    # syntax error). Same idea, hosttools-only commands. Passed here rather
    # than via EXTRA_OEMAKE because its value only survives "$@" quoting.
    oe_runmake BUILD_ID_CMD='cat $^ | md5sum | sed -e "s/^\(........\).*/0x\1/"' \
        -C ${S}/src bin-x86_64-efi/ipxe.efi
}

do_install[noexec] = "1"

# Deployed machine-suffix-free: wic's IMAGE_BOOT_FILES and the
# eicke-update-image-netboot* SWUPDATE_IMAGES lookup both use the plain name.
# Under efi-secure-boot (EICKE_SECURE_BOOT=1) the deployed ipxe.efi is signed
# with the UEFI db key so the firmware itself verifies it: iPXE is booted
# DIRECTLY from the UEFI A/B entries, with no shim/SELoader in between (shim
# chainloads a fixed next-stage name, incompatible with BootNext pointing at
# ipxe-a.efi/ipxe-b.efi). Deliberately uefi_sb_sign (db key), NOT sb_sign
# (which routes to the shim vendor_cert when MOK_SB=1). The unsigned binary is
# kept under efi-unsigned/ (kernel convention) for negative Secure Boot tests.
python do_deploy() {
    import shutil
    src = d.expand('${S}/src/bin-x86_64-efi/ipxe.efi')
    deploydir = d.getVar('DEPLOYDIR')
    if d.getVar('UEFI_SB') == '1':
        uefi_sb_sign(src, deploydir + '/ipxe.efi', d)
        os.makedirs(deploydir + '/efi-unsigned', exist_ok=True)
        shutil.copyfile(src, deploydir + '/efi-unsigned/ipxe.efi')
    else:
        shutil.copyfile(src, deploydir + '/ipxe.efi')
}
addtask deploy after do_compile before do_build
do_deploy[prefuncs] += "${@bb.utils.contains('DISTRO_FEATURES', 'efi-secure-boot', 'check_deploy_keys', '', d)}"

# Nothing is packaged; the artifact is consumed from DEPLOY_DIR_IMAGE.
EXCLUDE_FROM_WORLD = "1"
