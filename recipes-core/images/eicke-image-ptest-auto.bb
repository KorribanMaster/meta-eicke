SUMMARY = "eicke-image-ptest that auto-runs the zynq-rproc ptest at boot"
require recipes-core/images/eicke-image-ptest.bb
IMAGE_INSTALL:append = " zynq-rproc-autotest"
