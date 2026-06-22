# Shared SWUpdate signing configuration, required by every eicke-update-image*
# recipe so all .swu bundles are signed with the same key.
#
# SWUpdate verifies a signature over sw-description (the manifest), and the
# manifest carries the sha256 of every artifact, so signing the manifest
# transitively authenticates the whole bundle. We use RSA-PSS (deterministic
# salt length -2, sha256) per the meta-swupdate swupdate-common class.
#
# The private key lives OUTSIDE the layer/git. The host keeps it at ~/yocto/keys
# and the build container mounts that dir read-only at /keys (see the docker run
# -v ~/yocto/keys:/keys:ro flag), so the in-build path is /keys/swupdate-priv.pem.
# Override EICKE_KEYS_DIR in local.conf for a different layout. The matching
# public key is shipped in the images by swupdate-public-key.bb so the running
# swupdate can verify (-k /etc/swupdate/public.pem).
SWUPDATE_SIGNING = "RSA-PSS"

EICKE_KEYS_DIR ??= "/keys"
SWUPDATE_PRIVATE_KEY ?= "${EICKE_KEYS_DIR}/swupdate-priv.pem"
