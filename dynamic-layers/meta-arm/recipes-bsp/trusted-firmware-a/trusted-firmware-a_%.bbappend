# te0820 (Zynq UltraScale+): TF-A bl31 for U-Boot's u-boot.itb. The zynqmp
# platform defaults fit the U-Boot side: bl31 runs from OCM at 0xfffea000
# (U-Boot's CONFIG_BL31_LOAD_ADDR default), RESET_TO_BL31, console on the
# Cadence UART0 (the board console). U-Boot takes bl31.bin from this recipe's
# sysroot (/firmware/trusted-firmware-a) as BL31, see the u-boot bbappend.
COMPATIBLE_MACHINE:te0820 = "te0820"
TFA_PLATFORM:te0820 = "zynqmp"
TFA_BUILD_TARGET:te0820 = "bl31"
TFA_INSTALL_TARGET:te0820 = "bl31"

# The zynqmp platform forces LTO (ENABLE_LTO := 1, so that bl31 fits in OCM),
# which needs gcc as the linker driver (-fuse-linker-plugin); meta-arm passes
# plain ld ("ld: -f may not be used without -shared"). TF-A detects a gcc
# linker and prefixes its linker options with -Wl, itself. meta-arm builds
# its LD= make argument from LD (which it does not export), so setting LD to
# the compiler here changes that one argument, whatever its position.
LD:te0820 = "${CC}"
