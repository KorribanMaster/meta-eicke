# Development

## Pre-commit hooks

This project uses pre-commit hooks to verify your commited files follow
most of the guidelines of this project. After obtaining a local copy of
this repository you must install the pre-commit hooks.

````shell
# Install the pre-commit tool
python3 -m pip install pre-commit
# Install the hooks
pre-commit install
````

In general all pre-commit hooks should run on every commit however tools are not
perfect. In case a tool generates a false positive or it was decided that a deviation
is accepted a tool can be skipped like this:

````shell
# e.g. skipping flake8
SKIP=flake8 git commit
````

## Commits

A commit shall address a modification addressing one thing only. E.g.
- Modify(add/remove) one recipe
- Set preferred version for one recipe
- Modify(add/remove) one machine
- Modify(add/remove) one script

Commits shall follow the conventional commit schema

