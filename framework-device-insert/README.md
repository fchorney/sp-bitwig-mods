# Devices: Framework Device Insert

> **Built against**: Bitwig 6.1.7 (macOS, universal)
>
> **Will map to**: Bitwig 6.1.7 on macOS only, for now
>
> **Mod ID**: framework-device-insert
>
> **Prerequisites**: Framework: Nitro Devices (`custom-device-registrar`)

## What does this do?

Framework: Nitro Devices registers your own devices under a path like `devices/Foo.bwdevice`
and shows them in the browser. Inserting one from the browser works. Inserting one by its UUID
does not: a controller script's `insertBitwigDevice(UUID)`, for example, fails with
`Error loading file .../Bitwig Studio.app/Contents/Resources/Library/devices/Foo.bwdevice`,
because Bitwig's UUID-to-file lookup joins the registry path to its own Library folder.

This mod changes that one lookup. When the file is not in Bitwig's Library, it returns the file
with the same UUID from the folders chosen in Patchbay's Device Folders. Bitwig's own devices
are unchanged: their files exist, so the stock answer is used.

The folders are scanned the first time a lookup misses, and again when a UUID is not found or
its file is gone. A rebuilt device at the same path is read fresh on the next insert.

## How do you use it?

Turn it on in Patchbay together with Framework: Nitro Devices. Each lookup it answers is
written to Bitwig's log under the mod's name.

## Tested

| Date | Bitwig | Patchbay | Result |
|---|---|---|---|
| 2026-11-25 | 6.1.7 macOS | BML 0.36.0-dev.17 | A device from Framework: Nitro Devices 1.3.0-dev.3 inserted by UUID from a controller script 16 times in a row, each from the folder file (first lookup scanned 1 folder, 2 files, 2 ms). Before: every insert failed with FileNotFoundException. |
