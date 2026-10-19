# Testing

There are no unit tests: the images are verified by booting them under QEMU
and driving the guest. The tests are a pytest suite in
[`scripts/test/`](../scripts/test/). They run **inside the build container**
(with `/dev/kvm` for x86; the image ships pytest and pexpect) after sourcing
`integration-init-build-env`, and find everything via `$BUILDDIR`.

## Running

```sh
pytest meta-eicke/scripts/test --build        # build the images, run all tests
pytest meta-eicke/scripts/test/test_x86_ab.py # one machine, images already built
pytest meta-eicke/scripts/test -k rollback    # by name
pytest meta-eicke/scripts/test -m secureboot  # the x86 hardening test (see below)
```

Without `--build` a test fails if its image is not deployed. Serial logs go to
`$BUILDDIR/qemu-serial-<test>.log` (a failure report ends with the log's tail),
bitbake output for `--build` to `$BUILDDIR/pytest-bitbake-<machine>.log`.
Add `--junitxml=<file>` for CI.

| Module | Machine / image | Checks |
|---|---|---|
| `test_arm_ab.py` | qemuarm-uboot `eicke-image` | A/B update via the `.swu`, confirm on slot B, forced rollback to A (`ustate=3`) |
| `test_arm_lockdown.py` | qemuarm-uboot `eicke-image` | a `bootcmd`/`rootdev` written to the U-Boot env cannot reach the kernel cmdline |
| `test_x86_ab.py` | qemux86-64 `eicke-image` (GRUB, OVMF) | the same A/B cycle via grubenv |
| `test_imx8mp.py` | qemuimx8mp-evk `eicke-image-dev` | M7 rpmsg echo ptest, A/B update, confirm on slot B, ptest again |
| `test_prod_ab.py` | qemuarm-uboot `eicke-image-prod`, verified boot | see below |
| `test_hardening_x86.py` | qemux86-64 `eicke-image-prod`, Secure Boot | see below; marker `secureboot`, not in the default run |

Each scenario is one test class on one guest, with a test per step (boot,
update, confirm, rollback). The steps depend on each other: after the first
failure the rest of the class is reported as xfail.

## Prod images

Prod images log in over SSH only (key auth on port 2222; the serial console
needs the real root password). The tests use a test key,
`$BUILDDIR/test-ssh-key` (generated on first use, override with
`EICKE_TEST_KEY`), and the image must be built with `EICKE_SSH_PUBKEY` set to
its `.pub`. Prod images also only build with verified boot.

- `test_prod_ab.py` (qemuarm-uboot) always builds the image (mostly sstate),
  with `EICKE_VERIFIED_BOOT=1` and the test key passed through the
  environment, so no conf file changes: any qemuarm-uboot bitbake run without
  the switch deletes the deployed prod image as stale. It needs the key store
  (`/yocto/keys`, see [security](./security.md)). Checks: key login, read-only
  rootfs, `/etc` overlay, `/data` mounted from the boot disk, the signed kernel
  FIT booted on every boot, a tampered `.swu` refused, A/B update, confirm,
  rollback.
- `test_hardening_x86.py` (qemux86-64): SSH policy, kernel lockdown, module
  signatures, sysctls, firewall, hardware-compatibility gating. Build the x86
  prod image with the verified-boot knobs from `local.conf.sample` and the
  test key first, then run it with `-m secureboot`.

## Writing a test

[`eicke_qemu.py`](../scripts/test/eicke_qemu.py) has the guest drivers:

- `Console(cmd, log)`: QEMU on a pty (pexpect). `expect(regex, timeout)`,
  `login(hostname)` (root, no password), `run(cmd)` → `(rc, output)`,
  `out(cmd)` (output, must succeed), `reboot()`, `close()` (poweroff).
- `SshGuest(cmd, log, key)`: QEMU in the background, driven over SSH:
  `wait_up()` (waits for a new boot), `run`/`out`, `reboot()`, `close()`.
- `UBootEnv(guest)` / `GrubEnv(guest)`: `get(name)` / `set(name=value)` on the
  A/B boot env.

A test module starts its guest in a module-scoped fixture, using the
`build`, `builddir`, `deploy_dir` and `test_key` fixtures from
[`conftest.py`](../scripts/test/conftest.py). A machine with the standard A/B
cycle subclasses `ABCycle` from
[`ab_cycle.py`](../scripts/test/ab_cycle.py) and sets its hostname, `.swu`
device and env class (see `test_x86_ab.py`).
