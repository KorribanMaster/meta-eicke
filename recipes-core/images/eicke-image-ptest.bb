SUMMARY = "eicke-image plus the Zynq remoteproc/rpmsg ptest harness"
DESCRIPTION = "Test variant of eicke-image: adds ptest-runner and the \
zynq-rproc-test ptest (which pulls python3 + the driver/firmware) so the \
remoteproc/rpmsg suite can be run on the target — against a real RTU board or \
the sim/vfio-user device model — with `ptest-runner`."

require recipes-core/images/eicke-image.bb

IMAGE_INSTALL:append = " \
    ptest-runner \
    zynq-rproc-test-ptest \
    pciutils \
"
