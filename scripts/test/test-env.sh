# Shared setup for the eicke test drivers (sourced, bash). Run them inside the
# build container after sourcing integration-init-build-env, from anywhere.
[ -n "${BUILDDIR:-}" ] || { echo "BUILDDIR unset: source integration-init-build-env first" >&2; exit 1; }
TESTDIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEPLOY_DIR=$BUILDDIR/tmp/deploy/images

# SSH key the prod images trust in tests (EICKE_SSH_PUBKEY = its .pub).
# Generated on first use; kept in the build dir, never in the layer.
EICKE_TEST_KEY=${EICKE_TEST_KEY:-$BUILDDIR/test-ssh-key}
[ -f "$EICKE_TEST_KEY" ] ||
    ssh-keygen -q -t ed25519 -N '' -C eicke-prod-test -f "$EICKE_TEST_KEY"
SSH_OPTS="-p 2222 -i $EICKE_TEST_KEY -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=5 -o LogLevel=ERROR"

# qdrive <script.json> <qemu command...>: drive the serial console;
# log in $BUILDDIR/qemu-serial-<script>.log.
qdrive() {
    local json=$1
    shift
    QSERIAL_LOG=$BUILDDIR/qemu-serial-$(basename "$json" .json).log \
        QEMU_CMD="$*" python3 "$TESTDIR/qdrive.py" "$TESTDIR/$json"
}
