"""Shared fixtures for the eicke QEMU image tests (see doc/testing.md).

Run inside the build container after sourcing integration-init-build-env:
everything is found via $BUILDDIR.
"""

import os
import subprocess
from pathlib import Path

import pytest
from eicke_qemu import bitbake, tail


def pytest_addoption(parser):
    parser.addoption(
        "--build",
        action="store_true",
        help="build the images a test boots before booting them",
    )
    parser.addoption(
        "--board",
        metavar="HOST",
        help="a real board running eicke-image, reached over SSH as root "
        "(the board tests are skipped without it)",
    )


def pytest_collection_modifyitems(config, items):
    if config.getoption("--board"):
        return
    skip = pytest.mark.skip(reason="needs a board: --board HOST")
    for item in items:
        if "board" in item.keywords:
            item.add_marker(skip)


@pytest.fixture(scope="session")
def builddir():
    path = os.environ.get("BUILDDIR")
    if not path:
        pytest.exit("BUILDDIR unset: source integration-init-build-env first", 2)
    return Path(path)


@pytest.fixture(scope="session")
def deploy_dir(builddir):
    return builddir / "tmp" / "deploy" / "images"


@pytest.fixture(scope="session")
def test_key(builddir):
    """SSH key the prod images trust in tests (EICKE_SSH_PUBKEY = its .pub).

    Generated on first use; kept in the build dir, never in the layer.
    """
    key = Path(os.environ.get("EICKE_TEST_KEY", builddir / "test-ssh-key"))
    if not key.exists():
        subprocess.check_call(
            ["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "eicke-prod-test"]
            + ["-f", str(key)]
        )
    return key


@pytest.fixture(scope="session")
def build(request, builddir):
    """build(machine, *targets, env=None, always=False): bitbake the targets
    when --build is given (or always), then check that the files exist."""

    def _build(machine, *targets, env=None, always=False, needs=()):
        if always or request.config.getoption("--build"):
            bitbake(builddir, machine, targets, env)
        for f in needs:
            if not Path(f).exists():
                pytest.fail(f"{f} missing: build it or pass --build")

    return _build


# Steps of an @pytest.mark.incremental class build on each other: after the
# first failure the rest are reported as xfail instead of failing again.
def pytest_runtest_setup(item):
    if "incremental" in item.keywords:
        failed = getattr(item.parent, "_eicke_failed", None)
        if failed:
            pytest.xfail(f"previous step failed ({failed})")


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    report = (yield).get_result()
    if not report.failed:
        return
    if "incremental" in item.keywords:
        item.parent._eicke_failed = item.name
    # Attach the end of the guest's serial log to the failure report.
    for value in getattr(item, "funcargs", {}).values():
        log = getattr(value, "log", None)
        if log:
            report.sections.append((f"serial log tail ({log})", tail(log)))
