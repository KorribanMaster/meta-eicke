# Embedded Linux Configuration Management with Yocto

This document describes the design, maintenance and usage of embedded linux
configuration management using Yocto.

Since this documentation is along with the configuration elements as well,
the various artifacts produced by applying a certain configuration like
(installable software, license reports, sboms, vulnerability status)
are described as well.

## Usage scenarios

- Release an embedded Linux firmware (BSP software + middleware + application)
- Develop an application
- Monitor security vulnerabilities
- Integrate a new BSP software (boot loader, kernel, device tree) for a new hardware
- Update shared middle ware services like (secure) software update, secure boot,
  factory deployment, device provisioning, firmware reset, device monitoring
- Manage 3rd party licenses
- Manage SouP reports
- Update upstream software updates and patches
- Do configuration specific vulnerability tracking

## Players

**Yocto Engineer**
- Maintains and evolves the Yocto configuration management system
  - Prepares new configurations for releases
  - Updates middleware recipes
  - Provides development environments for BSP developer and application developer
  - Evaluates/updates Yocto upstream configuration elements
  - Integrates new upstream configuration elements (layers for new hardware, new middleware)
  - Retires old middleware
- Integrates builds and build tests into CI/CD

**Application developer**
- Uses Yocto (SDK) to build and deploy application to the target device
- Maintains application recipes.

**BSP developer**
- Creates and tests BSP software (bootloader, kernel, device tree, kernel modules)
- Integrates new boards into the Yocto configuration system
- Updates/(BBappends) BSP recipes

**Release authority**
- triggers release build
- audits that release builds are reproducible and integrity
- sign release builds (only signed builds are subject to secure software update)

## Table of Content

- Yocto
  - TODO Continuous Integration
  - TODO Yocto Configuration Guidance
  - [Application Development](./application-development.md)
  - [Release Procedure](./release-procedure.md)
  - [Contributing](./contribute.md)
- Security
  - [UEFI Secure Boot setup](./secure-boot-setup.md) (eicke-image-prod)
  - [TPM-sealed /data encryption](./encrypted-data-todo.md) (WIP design + remaining work)
- Images — [`images/`](./images/):
  - [eicke-image](./images/eicke-image.md),
  - [eicke-image-dev](./images/eicke-image-dev.md) (debug tools),
  - [eicke-image-prod](./images/eicke-image-prod.md) (hardened production image),
  - [eicke-update-image](./images/eicke-update-image.md)
- Machines — [`machines/`](./machines/):
  - [qemux86-64](./machines/qemux86-64.md) (incl. how to run & interact),
  - [genericx86-64](./machines/genericx86-64.md)
