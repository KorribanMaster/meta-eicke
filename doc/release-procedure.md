# Yocto based Software Releases

## Concepts

Yocto allows to configure and build customized Linux distribution for customized
embedded Linux hardware.

A installed Linux distribution is specific for a hardware/machine and is specific
with regards to the set of applications. It provides:

- Bootloader
- Linux kernel including the device tree
- root filesystem

The root filesystem contains:
- Init process
- Arbitrary services
- Applications
- Customization (fstab, users, network config, service config, app config, ...)

A software release in Yocto is always a release of complete distribution.
A software release must be

- Identifiable
- Reproducible.

## Applied Mechanisms for Release within Yocto context

### Recipe Repository Aspekts

A Yocto created **linux distribution** is composed of a **set of components**.
Those **components** have a **name**  (like 'linux-yocto' for the Linux kernel)
and have a **version** ('5.15' for the Linux kernel).

Providing a name and version assures indentifiability for a component.

A **component is build**, extended by configuration, packaged and deployed **by
specifications within recipes**. The build specification includes the
specification about which sources at which version/revision have to be used exactly
to build the component.

**Recipes** itself are **versionized** as well.
By pinning the version/revision of the recipes reproducibility is assured for the
components.

Recipes are organized in multiple git repositories.
Pinning all recipes requires the extraction of them from
the individual git repositories at the individual git revisions.

**Manifest files** are used to manage the **access** to those **repositories
at specific versions** by help of the *google repo tool*.

### Recipe Version Selection

Recipes for one component will exist in different version in parallel to build
components at different versions.

For a specific release build, for every component it needs to be uniquely determined
which version has to be build.

Yocto provides rules by layers and layer priorities and version ordering about which version
is to pick for a release. If the desired version is not the latest version that is picked
by Yocto's version resolution rules, an explicit component/recipe version must be given.

Thus preferred versions are collected in a preferred version file.

## Release Naming Scheme

Distribution release must be unique and do not require any other semantics to be encoded
into the name.

However, it would be nice if the release name is not too long and one can derive the
approximate time when the release was done and also it would be nice to be able to compare
two release names and know which one was earlier.

The following name scheme is proposed:

**yyQq-rn**

whereby
- `yy` - is the year when the release was done (element of [00..99], e.g. 23 for 2023)
- `q` - is the quarter when the release was done (element of {1,2,3,4}, e.g. 4 for 4th quarter)
- `n` - is a one digit, sequencial number (element [1..9])

This allows to do up to 9 releases per quarter. Later release in a quarter have higher numbers.
The first release in a quarter should start with 1.

## Actions for a Software Release

1. Determine the release name according to the scheme `yyQq-rn`.
2. Provide a fixed version recipes revision for all `*_git.bb` recipes.
3. Set the preferred version of all recipes in a config file named
   `meta-eicke/conf/distro/include/preferred-version_<yyQq-rn>.inc`
   The content of the file might look like:
   ```
   PREFERRED_VERSION_something = "7.0%"
   PREFERRED_VERSION_else= "1.2"
   ```
4. Pin the recipe base by creating a release manifest file named `<yyQq-rn>.xml`
   in folder `manifests/releases/` at the `eicke-manifests` repository.
   The manifest file must also contain the creation of a script link to be sourced
   at the Yocto build as part of the `eicke-manifests` project. The link name
   must follow the pattern `release_<yyQq-rn>_init-build-env` (underscores as
   delimiters — the init script parses the release version out of its own
   name and pins `DISTRO_VERSION` from it). e.g. for release `26Q3-r1`:
   ```xml
   <project name="meta-eicke" path="sources/meta-eicke" remote="github" revision="wrynose">
     <!-- Expose the build helper at the workspace root after `repo sync` -->
     <linkfile src="README.md" dest="README.md"/>
     <linkfile src="scripts/eicke-init-build-env" dest="release_26Q3-r1_init-build-env"/>
   </project>
   ```
5. TODO Workflow 
  

