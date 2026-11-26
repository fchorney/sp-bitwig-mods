# Devices: Missing Device Placeholder

> **Built against**: Bitwig 6.1.4 (macOS, universal)
>
> **Will map to**: every Bitwig build Patchbay's map packs translate macOS 6.1.4 to (offline preparation checked on macOS 6.1.4 and 6.1.7; tested live on macOS 6.1.4 and 6.1.7)
>
> **Mod ID**: bugfix-missing-device-placeholder

## What does this do?

Opening a project that uses a native device Bitwig can't find crashes Bitwig. This happens with
devices loaded by Framework: Nitro Devices when their folder is no longer in Device Folders, or
when a device's UUID changed. The log shows `Error loading device contents:
DeviceNotFoundException: Device not found with id: …`, then a fatal `NullPointerException` in
the GUI event thread. Bitwig has a "Plug-in missing" placeholder for plug-ins, but none for its
own kind of device.

With this mod, such a device opens as **Missing Device**: a placeholder that passes audio
through unchanged. Install the device again (or add its folder back) and reopen the project to
get it back.

**Saving the project while the device is missing replaces the device's settings** with the
placeholder's. Reopen the project without saving if you want to keep them.

## How do you use it?

Turn it on in Patchbay. Each placeholder it makes is written to Bitwig's log under the mod's
name, and kept in the mod's data folder (`placeholders/<uuid>.bwdevice`).
