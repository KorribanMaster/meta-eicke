# Shared production hardening, require'd by eicke-image-prod AND
# eicke-image-netboot-prod: real credentials, key-only SSH policy, and removal
# of the dev-build password laxness. Boot/storage hardening (secure-boot ESP,
# read-only rootfs, /etc overlay wiring) is per-image and stays in the recipes.

# Real password hashes (gitignored; copy from eicke-image-prod-creds.inc.sample).
require recipes-core/images/eicke-image-prod-creds.inc

inherit extrausers

# Undo the global dev laxness from local.conf's EXTRA_IMAGE_FEATURES so root has
# a real (non-empty) password and sshd isn't auto-loosened to permit root by
# password.
IMAGE_FEATURES:remove = "allow-empty-password empty-root-password allow-root-login"

# ---- SSH: deploy the ed25519 key for root + eicke, harden sshd --------------
# Keys live in root-owned /etc/ssh/authorized_keys.d/<user> (works with a
# read-only rootfs and avoids per-home ownership juggling).
EICKE_SSH_PUBKEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICNIz4EuNBI8LpsfjUk5F+fC0/6Q+STuvnbGuyYBvXm+ eicke@xps-arch"

eicke_prod_ssh_setup() {
    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root \
               ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke

    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d
    cat > ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf <<EOF
# Production SSH policy (eicke-prod-hardening.inc)
PermitRootLogin prohibit-password
PasswordAuthentication yes
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys /etc/ssh/authorized_keys.d/%u
EOF
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_prod_ssh_setup;"
