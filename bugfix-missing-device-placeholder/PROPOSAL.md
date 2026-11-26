# Proposal: a placeholder for missing devices in Framework: Nitro Devices

For: `custom-device-registrar` (Framework: Nitro Devices), Patchbay.
Working prototype: the separate mod `bugfix-missing-device-placeholder` (sp-bitwig-mods,
`bugfix-missing-device-placeholder/`, released as
[0.1.0-dev.1](https://github.com/fchorney/sp-bitwig-mods/releases/tag/bugfix-missing-device-placeholder-0.1.0-dev.1); source `src/dev/sp/bugfix/missingdevice/PlaceholderHooks.java`), written in
**6.1.4** names. Offline preparation passes on macOS 6.1.4 and 6.1.7. Live tests: macOS 6.1.4 with this build (the project
crashes Bitwig without the mod and opens with it), macOS 6.1.7 with the same hook in 6.1.7 names.
Below, names are given as 6.1.4 / 6.1.7.

## The problem

A project that uses a native device Bitwig can't find crashes Bitwig when it is opened. With the framework this
happens whenever a device's folder is no longer in Device Folders, the file was moved or deleted, or a device's UUID
changed between builds. Without Patchbay it presumably happens to any project that uses a framework device (not
tested).

Bitwig has a "Plug-in missing" placeholder for plug-ins, but none for its own kind of device, since its own devices
can't go missing. What happens (6.1.7 names, from the log and the decompiled classes):

1. Loading the device's contents (`utt.IHE()`) asks for the package file. `TL.MRY(UUID)` / `gEH.Gef(UUID)` calls
   `TL.cfM(UUID)` / `gEH.beI(UUID)` and, when that returns null, throws `DeviceNotFoundException`. (The framework's
   `packageFile` hook sits in front of this and falls through to it for an unknown UUID.)
2. `utt.IHE()` catches it and logs `Error loading device contents: ... Device not found with id: <uuid>`. The
   device is left without contents.
3. The GUI then reads the device's descriptor (`utt.GGt()`) from the licence check (`JXr`) and from the device view,
   and gets a `NullPointerException` on the GUI event thread: `Application crashing in subsystem GUI Event Thread`.

Guarding the descriptor getter isn't enough: we tried it, and the GUI survives, but the audio engine then segfaults
(exit code 139) when it connects to the project, because it is given a device with no contents.

## What to add

When the package lookup has no file, answer with a **placeholder package** instead of null, so the device loads real
contents:

- One hook: `REPLACE_CALL` of `TL.cfM(UUID)` inside `TL.MRY(UUID)`. If the stock call returns a file, return it
  unchanged; otherwise return the placeholder for that UUID. If making the placeholder fails, return null, so Bitwig
  behaves as it does today.
- The placeholder is a copy of a bundled template, with the template's UUID replaced by the missing one, written to
  the mod's data folder (`placeholders/<uuid>.bwdevice`) and reused after that.
- The template is stored **unencrypted** (header `BtWg00030002…`, plain metadata at offset 42, plain body at the
  body offset, the format Polarity's published devices use), so the UUID can be replaced in place: once as
  36-character text (metadata `device_id`) and twice as 16 raw bytes (metadata `device_uuid`, body root field
  `0x181`). Both have the same length as the template's, so no offsets change.
- The prototype's template is stock DC Offset (one `math/add` atom, DC defaults to 0, so audio passes through
  unchanged) renamed to **Missing Device**. Any pass-through device would do. A dedicated panel (a label naming the
  missing UUID, say) would be nicer, but needs a build tool.

Tested on macOS 6.1.7 with a project whose Sym EQ-5 had an unknown UUID: the project opens, the audio engine stays
up, the track shows **Missing Device** and audio passes through. It shows Bitwig's lock icon over the panel, because
the placeholder's UUID isn't in the framework's registry or entitlements. That seems right for a missing device.

## Things to decide

- **Settings are lost on save.** The placeholder has no parameters, so saving the project while the device is missing
  replaces the device's settings with the placeholder's. Reopening without saving keeps them. Bitwig keeps a missing
  plug-in's state; doing the same here would mean keeping the device's saved state, which this proposal doesn't do.
- **Every UUID lookup through `TL.MRY` now succeeds.** Not checked: whether any caller uses the exception to test
  whether a device exists (browser, presets, controller API). If so, the hook could be limited to the
  project-recall path (the call in `utt.IHE()`), or to UUIDs the framework has seen in a project.
- **Where it belongs.** It fits the framework, whose users are the ones whose devices go missing. It needs nothing
  from the framework, though, so it also works as a separate mod.

## Also found: BML's ENTRY rewrite fails verification on one method

While prototyping, an `ENTRY` hook on `utt.IHE()` (6.1.7) made the launch stop with
`BML launch failed with exit code 1`. The cause was only in `<BML>/state/launch-console.log`:

```
DEEP_VERIFY_FAIL com.bitwig.flt.document.core.master.device.utt java.lang.ClassFormatError:
StackMapTable format error: bad offset for Uninitialized in method 'void ...utt.IHE()'
```

`IHE()` contains `new PCp((bNN)(this.gNZ == null ? this : this.gNZ), "", true)`: the ternary inside the constructor
arguments gives a stack-map frame with an `Uninitialized(offset)` entry, and it looks as if that offset isn't moved
when code is inserted at the method's start. BML 0.36.0-dev.17, macOS 6.1.7. Reproducible offline with
`loader/prepare-mod.sh`.
