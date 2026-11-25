# Devices: Framework Device Help

> **Built against**: Bitwig 6.1.4 (macOS, universal)
>
> **Will map to**: every Bitwig build Patchbay's map packs translate macOS 6.1.4 to (offline preparation checked on macOS 6.1.4 and 6.1.7)
>
> **Mod ID**: framework-device-help

Devices loaded by Patchbay's **Framework: Nitro Devices** can bring their own help text.

Bitwig's help view (the **?** overlay on a device) shows, for every panel item, the texts stored under the item's help
id: `<help id>.title` and `<help id>.desc`, looked up in Bitwig's own translation tables. A custom device whose items
use help ids Bitwig doesn't know shows the raw keys (`In.title`, `.desc`). This mod adds a fallback: keys Bitwig doesn't
have are looked up in help files kept next to the devices.

## Help files

Any file named `*.help.properties` in the framework's device folders (the same folders and subfolders it scans for
devices), for example `Sym EQ-5.help.properties` next to `Sym EQ-5.bwdevice`. Java properties format, UTF-8:

```properties
sym-eq5.input_meter.title=Input Meter
sym-eq5.input_meter.desc=Level at the device input, before the Input knob
```

- Prefix the keys with something unique to your device, so two devices don't clash. Of two files with the same key,
  the first wins (folders in the framework's order, then by path).
- Bitwig's own texts always win: a key Bitwig has is never taken from a help file.
- Help files are read again when one changes, added or removed (checked at most once a second), so texts can be edited
  with Bitwig running: reopen the help view to see them.

## Needs

- Patchbay's Framework: Nitro Devices (`custom-device-registrar`), whose folder list it reads.

## Tested

| Date | Bitwig | Patchbay | Result |
|---|---|---|---|
| 2026-11-25 | 6.1.7 macOS | BML 0.36.0-dev.17 | 0.1.0-dev.1 (built against 6.1.7): a framework device's help view shows its `*.help.properties` texts; stock ids on the same device keep Bitwig's texts. |
| 2026-11-25 | 6.1.7 macOS (mod built against 6.1.4, mapped by Patchbay) | BML 0.36.0-dev.17 | 0.1.0-dev.2: same result. |
