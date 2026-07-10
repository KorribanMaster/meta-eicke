SUMMARY = "eicke-image plus the Zynq remoteproc/rpmsg ptest harness"
DESCRIPTION = "Test variant of eicke-image: adds ptest-runner and the \
zynq-rproc-test ptest (which pulls python3 + the driver/firmware) so the \
remoteproc/rpmsg suite can be run on the target — against a real RTU board or \
the sim/vfio-user device model — with `ptest-runner`."

# Zynq-RTU-over-PCIe test harness runs on the x86 host machines only.
COMPATIBLE_MACHINE = "qemux86-64|genericx86-64"

require recipes-core/images/eicke-image.bb

IMAGE_INSTALL:append = " \
    ptest-runner \
    zynq-rproc-test-ptest \
    pciutils \
"
