# Application Development

## Table of Content

- [Classic SDK aka Standard SDK](#classic-sdk-aka-standard-sdk)
- [Build Apps on Development Branches](#build-apps-on-development-branches)
- [Kernel development](#kernel-development)

## Classic SDK aka Standard SDK

Purpose is to provide a cross tool chain to that

- Acts as a wrapper around your build tool. It injects the toolchain specific
  settings. (compiler path, compiler flags, platform specific flags
- Builds the specified application targets for the target architecture.
- You need to manually transport `scp <build-artifact> <target-location>`
  the build artifact to the target machine.

To build an Standard SDK for a specific **machine** and an **image** run:

```sh
$ source integration-init-build-env
$ MACHINE=genericx86-64 bitbake eicke-image-dev -c populate_sdk
```
The machine architecture determines the cross-toolchain (ISA-selection, compile and link flags).
The image assures all `$DEPEND` libraries are installed.

This produces the SDK installer at the folder `<build-dir>/tmp/deploy/sdk`. The installer
should be run like:
```
$ cd tmp/deploy/sdk
$ #TODO
```
The installer asks for the deployment directory (you might just go for the default).
The installer output is like:
```
TODO
```
As written in the installer output, using the SDK cross-toolchain requires sourcing the environment script.

See the [Yocto doc](https://docs.yoctoproject.org/sdk-manual/working-projects.html#using-the-sdk-toolchain-directly)
on how to use standard SDK toolchain.

## Build Apps on Development Branches

In this scenario you build a complete image including the application under
development. But instead of building from main (stable and QA tested) branch,
you decide to build from you development aka feature branch and test the
build result in an integrated target environment.

Assuming you like to build and test your development state of an application in feature branch *feat/something* and want to build and
test that branch on the target image.

Here is what you can do:

1. Source your Yocto environment
2. prior to bitbake inject
   ```
   export BB_ENV_PASSTHROUGH_ADDITIONS="${BB_ENV_PASSTHROUGH_ADDITIONS} APP_BRANCH"
   export APP_BRANCH="feat/something"
   ```
   enforces the communication controller application to be build from development
   branch instead of the main branch.
3. Call bitbake `bitbake eicke-image-dev` to build the image containing your
   app at development state in branch *feat/something*.
4. Install the image and test.

> Note: In order to support that branch build inject application recipes must
> be sensitive on the above variables. It is done by:
> ```
> APP_BRANCH ?= "main"
> APP_SRCREV ?= "no-injected-ref"
>
> BPV ?= "b-0.1"
> PV = "${BPV}+git${SRCREV}"
> PKGV = "${GITPKGVTAG}"
> PR = "${INC_PR}.1"
>
> BRANCH_HANDLING = "${@ "branch=${APP_BRANCH}" if d.getVar('APP_SRCREV') == 'no-injected-ref' else 'nobranch=1' }"
> SRC_URI += "git://git@github.com/someapp.git;protocol=ssh;${BRANCH_HANDLING}"
> SRCREV = "${@ "${APP_SRCREV}" if d.getVar('APP_SRCREV') != 'no-injected-ref' else d.getVar('AUTOREV') }"
> ``` 
> within the recipe.

## Kernel development

Add `TOOLCHAIN_TARGET_TASK += "kernel-devsrc"` to your `local.conf`. This will create the necessary kernel sources in your sysroot environment.
