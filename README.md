# SP Bitwig Mods

Mods for Bitwig Studio, loaded with [Patchbay](https://github.com/shaboogen-dev/Patchbay).

> **These mods need Patchbay**: an open-source (MIT) third-party mod loader for Bitwig, not made
> by Bitwig. A mod is a folder that Patchbay loads into Bitwig when it starts; Bitwig's own
> installation is never modified. Without Patchbay a mod's zip does nothing.

## Mods

| Mod | ID | Built against | Version |
|---|---|---|---|
| [Mixer: Layout Abort Recovery](bugfix-layout-abort-recovery/README.md): stops big Mix views freezing when Bitwig gives up on a layout | `bugfix-layout-abort-recovery` | 6.1.4 macOS | 0.1.0-dev.2 |
| [Devices: Framework Device Insert](framework-device-insert/README.md): devices from Framework: Nitro Devices can be inserted by UUID, not only from the browser | `framework-device-insert` | 6.1.4 macOS | 0.1.0-dev.2 |
| [Devices: Framework Device Help](framework-device-help/README.md): devices from Framework: Nitro Devices can bring their own help-view texts (`*.help.properties` next to them) | `framework-device-help` | 6.1.4 macOS | 0.1.0-dev.2 |
| [Devices: Missing Device Placeholder](bugfix-missing-device-placeholder/README.md): a project using a device Bitwig can't find opens with a pass-through placeholder instead of crashing | `bugfix-missing-device-placeholder` | 6.1.4 macOS | 0.1.0-dev.1 |

## Install

1. Install [Patchbay](https://github.com/shaboogen-dev/Patchbay).
2. Download the mod's zip from this repository's [Releases](../../releases).
3. In Patchbay, **Add Mod…** and choose the zip, tick the mod, and **Load**.

## Layout

One folder per mod, in Patchbay's source layout (Patchbay `docs/sdk/mods.md`):

| Path | Contents |
|---|---|
| `<mod-id>/mod.json` | The manifest without hooks; the build adds them |
| `<mod-id>/build.json` | Build settings |
| `<mod-id>/src/` | Java source, compiled into `helper.jar` |
| `<mod-id>/README.md` | The mod's README, shown on GitHub and in Patchbay |
| `tools/build_mod.sh` | Builds, installs and packages a mod ([BUILDING.md](BUILDING.md)) |
| `tools/release.sh` | Tags and publishes a mod's release ([BUILDING.md](BUILDING.md#release)) |

## License

MIT ([LICENSE](LICENSE)).
