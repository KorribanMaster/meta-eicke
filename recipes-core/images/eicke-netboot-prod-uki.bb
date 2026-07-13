SUMMARY = "Signed UKI for eicke-image-netboot-prod (stub + bzImage + cpio + cmdline)"
DESCRIPTION = "Builds a Unified Kernel Image with ukify: systemd-boot stub + the \
machine bzImage + eicke-image-netboot-prod's own cpio.gz as initrd + a fixed \
embedded command line, signed (outer PE and the embedded kernel) with the UEFI \
Secure Boot db key. iPXE chains it over HTTP and the firmware's LoadImage \
verifies it -- the only clean way to get kernel+initrd+cmdline authenticated \
under Secure Boot, since iPXE defers image execution to the firmware. \
NOT oe-core's uki.bbclass: that class must be inherited by the image itself, \
which would be circular here (the UKI initrd IS the image's cpio), and it \
computes the initrd name without the .rootfs infix this build uses."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# UKI/UEFI netboot payload is x86-only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

# ukify from systemd-boot-native (python3-pefile-native staged explicitly:
# native RDEPENDS don't reach the recipe sysroot); the x86-64 stub
# linuxx64.efi.stub from systemd-boot:do_deploy; sbsign from sbsigntool-native
# via user-key-store's conditional DEPENDS; os-release for the UKI section.
DEPENDS = "systemd-boot systemd-boot-native python3-pefile-native os-release virtual/kernel"
# python3native puts the sysroot python3 (which has pefile) on PATH -- ukify's
# shebang is `env python3` and must not resolve to the container's python.
inherit deploy user-key-store python3native

COMPATIBLE_HOST = "x86_64.*-linux"
PACKAGE_ARCH = "${MACHINE_ARCH}"

python () {
    if not bb.utils.contains('DISTRO_FEATURES', 'eicke-verified-boot', True, False, d):
        raise bb.parse.SkipRecipe("eicke-netboot-prod-uki requires EICKE_VERIFIED_BOOT=1")
}

# Deployed name consumed by boot.ipxe / the sim script / docs.
UKI_FILENAME = "eicke-netboot-prod-uki.efi"
# Fixed, signed cmdline. No rdinit=: the default /init is the netboot-prod
# wrapper (data mount + /etc overlay). No root=: the initramfs IS the rootfs.
UKI_CMDLINE = "console=ttyS0"
UKI_INITRD = "${DEPLOY_DIR_IMAGE}/eicke-image-netboot-prod-${MACHINE}.rootfs.cpio.gz"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
do_install[noexec] = "1"

python do_uki() {
    import bb.process
    deploy = d.getVar('DEPLOY_DIR_IMAGE')
    keys = uefi_sb_keys_dir(d)
    initrd = d.getVar('UKI_INITRD')
    for f in (deploy + '/linuxx64.efi.stub', deploy + '/bzImage', initrd):
        if not os.path.exists(f):
            bb.fatal("UKI input missing: %s" % f)
    cmd = (
        "ukify build"
        " --efi-arch x64"
        " --stub %s/linuxx64.efi.stub"
        " --linux %s/bzImage"
        " --initrd %s"
        " --cmdline '%s'"
        " --os-release @%s%s/lib/os-release"
        " --signtool sbsign --sign-kernel"
        " --secureboot-private-key %sDB.key"
        " --secureboot-certificate %sDB.crt"
        " --output %s/%s"
    ) % (deploy, deploy, initrd, d.getVar('UKI_CMDLINE'),
         d.getVar('RECIPE_SYSROOT'), d.getVar('prefix'),
         keys, keys, d.getVar('B'), d.getVar('UKI_FILENAME'))
    bb.note("uki: %s" % cmd)
    bb.process.run(cmd, shell=True)
}
do_uki[dirs] = "${B}"
do_uki[cleandirs] = "${B}"
# Deploy-dir inputs: re-run when the image, kernel or stub change.
do_uki[depends] += " \
    eicke-image-netboot-prod:do_image_complete \
    virtual/kernel:do_deploy \
    systemd-boot:do_deploy \
"
do_uki[prefuncs] += "check_deploy_keys"
addtask uki after do_configure before do_deploy

do_deploy() {
    install -m 0644 ${B}/${UKI_FILENAME} ${DEPLOYDIR}/${UKI_FILENAME}
}
addtask deploy after do_uki before do_build

# Consumed from DEPLOY_DIR_IMAGE by the netboot server; nothing packaged.
EXCLUDE_FROM_WORLD = "1"
