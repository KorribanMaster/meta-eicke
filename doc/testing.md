# Testing

There are no unit tests: the images are verified by booting them under QEMU
and driving the guest. The drivers live in [`scripts/test/`](../scripts/test/)
and run **inside the build container** (with `/dev/kvm` for x86) after
sourcing `integration-init-build-env`; they find everything via `$BUILDDIR`.

## Regression run

```sh
meta-eicke/scripts/test/regression.sh --build     # build the images, run all tests
meta-eicke/scripts/test/regression.sh x86-ab      # one test, images already built
```

| Test | Machine / image | Checks |
|---|---|---|
| `arm-ab` | qemuarm-uboot `eicke-image` | A/B update via the `.swu`, confirm on slot B, forced rollback to A (`ustate=3`) |
| `arm-lockdown` | qemuarm-uboot `eicke-image` | a `bootcmd`/`rootdev` written to the U-Boot env cannot reach the kernel cmdline |
| `x86-ab` | qemux86-64 `eicke-image` (GRUB, OVMF) | the same A/B cycle via grubenv |
| `imx8mp` | qemuimx8mp-evk `eicke-image-dev` | M7 rpmsg echo ptest, A/B update, confirm on slot B, ptest again |
| `prod-ab` | qemuarm-uboot `eicke-image-prod`, verified boot | see below |

Each test prints `REG <test> rc=<n>`; its output is in
`$BUILDDIR/regression-<test>.out` and the serial log in
`$BUILDDIR/qemu-serial-<script>.log`.

## Prod images

Prod images log in over SSH only (key auth on port 2222; the serial console
needs the real root password). The drivers use a test key,
`$BUILDDIR/test-ssh-key` (generated on first use, override with
`EICKE_TEST_KEY`), and the image must be built with `EICKE_SSH_PUBKEY` set to
its `.pub`. Prod images also only build with verified boot.

- `prod_ab_test.sh [--build]` (qemuarm-uboot): `--build` builds the image with
  `EICKE_VERIFIED_BOOT=1` and the test key passed through the environment, so
  no conf file changes. It needs the key store (`/yocto/keys`, see
  [security](./security.md)). Checks: key login, read-only rootfs, `/etc`
  overlay, `/data` mounted from the boot disk, the signed kernel FIT booted on
  every boot, a tampered `.swu` refused, A/B update, confirm, rollback.
- `hardening_test.sh` (qemux86-64): SSH policy, kernel lockdown, module
  signatures, sysctls, firewall, hardware-compatibility gating. Build the x86
  prod image with the verified-boot knobs from `local.conf.sample` and the
  test key first.

## Writing a serial test

`qdrive.py` runs the command in `QEMU_CMD` on a pty and works through a JSON
list of steps: `{"expect": "<regex>", "timeout": <s>}`, `{"send": "<line>"}`,
`{"sendraw": "<text>"}`, `{"sleep": <s>}`. `test-env.sh` provides
`qdrive <script.json> <qemu command...>` for the shell drivers.
