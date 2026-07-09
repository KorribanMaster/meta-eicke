# eicke-image-prod

The **hardened production** build of [eicke-image](eicke-image.md): the same
GRUB-EFI A/B + SWUpdate base, with real credentials, SSH key authentication,
UEFI Secure Boot, a read-only rootfs with a persistent `/etc` overlay, and only
the required kernel modules.

- Recipe: `recipes-core/images/eicke-image-prod.bb` (`require`s `eicke-image.bb`)
- Build: `bitbake eicke-image-prod`
- Update bundle: `bitbake eicke-update-image-prod` → `eicke-update-image-prod-<machine>.rootfs.swu`
- Artifacts: `tmp/deploy/images/<machine>/eicke-image-prod-<machine>.rootfs.wic`

## Credentials

The base/dev images allow empty-password root login (template
`EXTRA_IMAGE_FEATURES`); prod removes those features and sets **real password
hashes** for `root` and the `eicke` user via `extrausers`. The hashes live in
`recipes-core/images/eicke-image-prod-creds.inc`, which is **gitignored** so
secrets never land in git — the recipe `require`s it, so the build fails loudly
if it is missing.

You normally don't have to create it yourself: on first init,
`integration-init-build-env` (i.e. `scripts/eicke-init-build-env`) generates the
file from `eicke-image-prod-creds.inc.sample` with **random passwords** and
prints them **once** — write them down. To choose your own passwords, edit the
generated file following the instructions in the sample (hash with
`openssl passwd -6`, escape every `$` as `\$`), or delete it and re-source the
init script for a fresh random pair.

SSH is hardened accordingly: key authentication via a deployed ed25519 key in
`/etc/ssh/authorized_keys.d/<user>`, `PermitRootLogin prohibit-password`.

## Hardening vs the base image

- **UEFI Secure Boot** — signed chain shim → SELoader → grub → kernel from
  meta-secure-core, ESP populated from the rootfs's `/boot/efi` via
  `eicke-ab-prod.wks.in`. Setup, key generation (`/yocto/keys/sb-user`) and
  QEMU verification: [doc/secure-boot-setup.md](../secure-boot-setup.md).
- **Read-only rootfs** with a persistent **`/etc` overlay** on the `data`
  partition (`overlayfs-etc` preinit); `/var` stays volatile, with a tmpfs over
  `/var/lib/systemd` for services using `StateDirectory=`.
- **Minimal kernel modules** — drops the catch-all `kernel-modules`, keeps the
  product NIC/GPU/remoteproc modules.
- **Encrypted `/data` (LUKS2, TPM2-sealed)** — implemented but not yet active;
  design and remaining work: [doc/encrypted-data-todo.md](../encrypted-data-todo.md).

## Update an A/B device with the prod image

Same flow as the base, using the prod bundle — see
[eicke-update-image](eicke-update-image.md) for the A/B mechanics. Bundles are
signed (`swupdate-signing.inc`); the private key is expected at
`/yocto/keys/swupdate-priv.pem` inside the build container (host:
`~/yocto/keys`, mounted by `dock.sh`).
