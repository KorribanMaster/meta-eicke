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
# Weak default so a deployment (or CI/emulation test) can inject its own key
# from local.conf/auto.conf without touching the recipe.
EICKE_SSH_PUBKEY ?= "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICNIz4EuNBI8LpsfjUk5F+fC0/6Q+STuvnbGuyYBvXm+ eicke@xps-arch"

eicke_prod_ssh_setup() {
    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root
    echo "${EICKE_SSH_PUBKEY}" > ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/root \
               ${IMAGE_ROOTFS}${sysconfdir}/ssh/authorized_keys.d/eicke

    install -d -m 0755 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d
    cat > ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf <<EOF
# Production SSH policy (eicke-prod-hardening.inc).
# Key-only over the network: password and interactive auth are disabled for
# sshd ONLY -- local console (getty) password login is untouched.
# PermitRootLogin stays prohibit-password (not "no"): the eicke user has
# shell /bin/sh and the image ships no sudo, so key-based root SSH is the
# only remote administration path. Revisit if sudo/doas is ever added.
PermitRootLogin prohibit-password
PasswordAuthentication no
# openssh is built without PAM (no "pam" DISTRO_FEATURE) and the oe-core
# default sshd_config already disables this; kept explicit so the policy
# stands on its own even if the defaults change.
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys /etc/ssh/authorized_keys.d/%u
EOF
    chmod 0644 ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config.d/99-eicke-prod.conf
}
ROOTFS_POSTPROCESS_COMMAND += "eicke_prod_ssh_setup;"

# ---- Runtime hardening: sysctls + default-deny firewall ----------------------
# Prod-only on purpose (dev keeps kernel defaults and open ports for
# debugging). eicke-sysctl-hardening: /etc/sysctl.d (kptr/dmesg restrictions,
# Yama ptrace scoping, unprivileged-BPF off, anti-spoofing). eicke-firewall:
# nftables default-drop inbound, only SSH (22) + SWUpdate web UI (8080) open,
# loaded before network-pre.target.
IMAGE_INSTALL:append = " eicke-sysctl-hardening eicke-firewall"

# ---- Verified boot is mandatory for production builds ------------------------
# Building a prod image without EICKE_VERIFIED_BOOT=1 used to silently produce
# a hardened-but-unverified image. Refuse instead. SkipRecipe (not bb.fatal):
# an anonymous-python bb.fatal would abort parsing for EVERY bitbake target,
# while skipping makes only the prod-image targets fail, loudly:
#   ERROR: Nothing PROVIDES 'eicke-image-prod' ... skipped: production images
#   require EICKE_VERIFIED_BOOT=1 ...
# Same mechanism eicke-image-netboot-prod already uses (its own check stays:
# it additionally needs the signing keys and must never be overridden).
# For qemu bring-up without a key store, set EICKE_ALLOW_UNVERIFIED_PROD = "1"
# in local.conf -- the build then proceeds with a warning.
python () {
    if d.getVar('EICKE_VBOOT') == '1':
        return
    if d.getVar('EICKE_ALLOW_UNVERIFIED_PROD') == '1':
        bb.warn("%s: built WITHOUT verified boot (EICKE_ALLOW_UNVERIFIED_PROD=1) "
                "-- not a releasable image" % d.getVar('PN'))
        return
    raise bb.parse.SkipRecipe("production images require EICKE_VERIFIED_BOOT=1 "
        "(set EICKE_ALLOW_UNVERIFIED_PROD=1 only for qemu bring-up)")
}
