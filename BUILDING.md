# Building

`tools/build_mod.sh <mod-id>` builds a mod with the BML mod kit, following Patchbay's
`docs/sdk/building.md` (the manual route). It runs on macOS and Linux.

## What you need

| | |
|---|---|
| JDK | 21 or later (`javac --release 21`) |
| `bitwig.jar`, `libs.jar` | from a Bitwig install of the exact build in the mod's `built_against` (e.g. 6.1.7 macOS: `Bitwig Studio.app/Contents/Java/`) |
| Patchbay (BML) install | its `modkit/` (`bml-modkit.jar`, `bml-api.jar`) and `mods/` folders |
| Patchbay repository clone | releases only: its `tools/mod_uses.py` writes `uses.tsv` |
| Python 3, `zip` | |

The mods use the obfuscated Bitwig names of their `built_against` build, so they compile only
against that build's `bitwig.jar`.

## Variables

```sh
export BML="$HOME/path/to/BML-0.36.0-dev.17-macos"         # Patchbay install
export BITWIG_JAR="/Applications/Bitwig Studio.app/Contents/Java/bitwig.jar"
export LIBS_JAR="/Applications/Bitwig Studio.app/Contents/Java/libs.jar"
export PATCHBAY="$HOME/path/to/Patchbay"                    # releases only
```

## Build

```sh
tools/build_mod.sh bugfix-layout-abort-recovery            # build and install into $BML/mods/
INSTALL=0 tools/build_mod.sh bugfix-layout-abort-recovery  # build only, into build/out/
RELEASE=1 tools/build_mod.sh bugfix-layout-abort-recovery  # also build/releases/<id>-<version>.zip
```

Steps: `bml-modkit.jar compile-jar` (once per `bitwig.jar`, cached in `build/cache/`), `javac`,
`bml-modkit.jar finish` (checks every `@Hook` against Bitwig's jar and writes `hooks.json`),
`helper.jar`, then the shipped `mod.json` (the source manifest plus the hooks). A release
also adds `src/`, `build.json`, `uses.tsv` and `SHA256SUMS.txt`, and zips the folder with
the mod ID at the top.

Restart Patchbay's window (or **Refresh**) after installing, so it prepares the new build.

## Release

Releases are built locally: the build needs Bitwig's own jars, which stay off GitHub.

```sh
tools/release.sh bugfix-layout-abort-recovery
```

It needs a clean working tree on `main` and a version in `mod.json` that has no tag yet. It
builds the release zip, checks its `SHA256SUMS.txt`, tags `<mod-id>-<version>`, pushes to
`origin` and `nas`, and creates the GitHub release with the zip and
`SHA256SUMS-<mod-id>-<version>.txt`. A version with a pre-release part (`0.1.0-dev.1`) is
published as a pre-release.
