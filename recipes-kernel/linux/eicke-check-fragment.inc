# Fail the build if any =y of the fragments in EICKE_CHECK_FRAGMENTS (file
# names from SRC_URI) did not make it into .config. Kconfig silently drops a
# symbol whose parent menuconfig or dependency is off, and
# kernel_configcheck does not always report it; the result is a kernel that
# boots without the driver. Same check as the qemuimx8mp-evk one in the
# linux-yocto bbappend.
EICKE_CHECK_FRAGMENTS ??= ""

do_eicke_check_fragment() {
    missing=""
    for frag in ${EICKE_CHECK_FRAGMENTS}; do
        for opt in $(grep -E '^CONFIG_[A-Z0-9_]+=y' ${UNPACKDIR}/$frag); do
            grep -qx "$opt" ${B}/.config || missing="$missing ${opt%=y}"
        done
    done
    [ -z "$missing" ] || bbfatal "options of ${EICKE_CHECK_FRAGMENTS} missing from .config (dependency off?):$missing"
}
addtask eicke_check_fragment after do_kernel_configme before do_compile
