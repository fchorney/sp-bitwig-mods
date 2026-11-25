# Proposal: help texts for custom devices in Framework: Nitro Devices

For: `custom-device-registrar` (Framework: Nitro Devices), Patchbay.
Working prototype: the separate mod `framework-device-help` (sp-bitwig-mods, `framework-device-help/`; source
`src/dev/sp/devices/frameworkhelp/HelpHooks.java`), built against Bitwig
**6.1.7 macOS**. The Bitwig names below are 6.1.7's. A version for the framework has to be rewritten in 6.1.4 names
(see "Finding the hook in 6.1.4").

## The problem

Bitwig's help view is the **?** overlay on a device. It draws a callout for each panel item that has a help id (field
`0x292f` on the panel item). The callout's text comes from Bitwig's translation tables:

- `<help id>.title`, `<help id>.desc`
- in `Resources/localization/Devices-resources.properties`, e.g. `eq-5.amount.desc=Scales the gain setting of all bands, moderating the effect`

For knobs, choosers and toggles, the title is the parameter's name plus a range or kind added by Bitwig (`[±24.0 dB]`,
`[toggle]`, `[various options]`). The help id's `.desc` supplies the description.

A custom device loaded through the framework can only use help ids that Bitwig's tables already contain. Any other id
shows the raw key in the help view (`In.title`, `In.desc`, or just `.desc` for an empty id), and Bitwig logs
`base-localization error: Localized text for key "..." not found` each time. In practice:

- a custom device can't describe anything Bitwig doesn't already have a text for;
- reusing a stock id gives the stock device's wording, which is often wrong for the custom device (for example "Learn
  Make-up Gain" on a learn button that writes an Output Gain).

## What to add

The framework already scans the user's device folders for `.bwdevice` / `.bwmodulator` / `.bwmodule` files. The
proposal: in the same folders, also pick up **help files**, and answer translation lookups that Bitwig can't answer from
them.

### File convention

- Any file named `*.help.properties` in a listed folder or its subfolders, with the same skip rules as the device scan
  (hidden folders, `auto-backups`, links followed, 32 levels). The natural place is next to the device:
  `Sym EQ-5.help.properties` beside `Sym EQ-5.bwdevice`.
- Java properties format, read as **UTF-8** (`Properties.load(Reader)`), so `\n` in a value is a line break in the help
  view.
- Keys are full translation keys. Device authors should prefix them with something unique to their device, for
  example:

  ```properties
  sym-eq5.input_meter.title=Input Meter
  sym-eq5.input_meter.desc=Level at the device input, before the Input gain
  sym-eq5.fx_chain.title=FX Chain
  sym-eq5.fx_chain.desc=Effects placed here process the EQ'd signal; the mirrored EQ after them undoes the EQ.
  ```

  The panel items then carry those help ids (`sym-eq5.input_meter`, ...), written into the `.bwdevice` by whatever tool
  builds it.

### Behaviour

1. **Bitwig always wins.** A help file is consulted only for a key that no Bitwig table has, so a help file can never
   change Bitwig's own texts, including stock devices' texts.
2. **First file wins** for a key present in two help files: folders in the configured order, then files sorted by path
   (the same order as the device scan's "first wins").
3. **Live reload.** Help files are read again when one is added, removed or changed, so authors can edit texts with
   Bitwig running and just reopen the help view. The prototype checks a stamp of all help files' paths, sizes and
   modification times, at most once a second, and only on a lookup that missed in Bitwig's tables.
4. **Cheap when unused.** The hook adds one null check to every translation lookup. The help files are only looked at
   on a miss, and a miss already logs an error in stock Bitwig, so misses are rare in normal use.
5. **Failure-safe.** An unreadable help file is skipped and logged once, and any exception in the lookup returns
   Bitwig's own result (null). The hook must never break Bitwig's text lookup.
6. Answering the lookup also stops Bitwig's "Localized text ... not found" log line for those keys, because the warning
   comes after the miss.

## The hook (6.1.7 names)

The translation tables are instances of `mjr` (extends the abstract `Yjl`, one instance per resource bundle). On 6.1.7:

```
public abstract class Yjl {
  public final String beI(String key, boolean warn, Object... args);  // public lookup
  private String Gef(String key, boolean warn, Object[] args);         // calls beI(key, args); if null and warn:
                                                                        //   logs "Localized text for key "" not found",
                                                                        //   returns beI(key) = the key itself
  protected abstract String beI(String key, Object[] args);           // the table's text, or null
}
public class mjr extends Yjl {
  protected String beI(String key, Object[] args);  // synchronized map get on the loaded bundle; null when missing
}
```

The prototype wraps `mjr.beI(String, Object[])`. For keys with arguments, the caller in `Yjl` formats the returned
string with `MessageFormat`, so a help text works like a Bitwig text there too:

```java
@Hook(id = "framework-device-help-text", target = mjr.class,
      method = "beI(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
      at = At.WRAP_RETURN, allReturns = true)
public static String text(@Return String found, @Arg(0) String key) {
    if (found != null || key == null) return found;   // Bitwig's own text wins
    try {
        return current().get(key);                      // help files' map (reloaded on change), or null
    } catch (Throwable t) {
        MOD.log("help lookup of " + key + " failed", t);
        return null;
    }
}
```

`allReturns = true` is used only so that `@Arg(0)` is safe to read (the method has a single return, but the SDK notes
that `@Arg` at a final return can fail verification).

The rest of the prototype (`HelpHooks.java`, about 170 lines) is the folder list, read from the framework's
`config.json` `folders`, the `*.help.properties` walk and the reload stamp. Inside the framework itself this would
reuse its own folder list and scan, so the mod-to-mod config reading goes away.

### Finding the hook in 6.1.4

- `jarsearch.py <bitwig.jar> strings "Localized text for key"` finds the abstract lookup class (`Yjl` on 6.1.7). Its
  private method that logs the warning calls the abstract `(String, Object[]) -> String` lookup first.
- `jarsearch.py <bitwig.jar> subclasses <that class>` gives the one implementation (`mjr` on 6.1.7). Hook its
  `(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;` override.
- Hooking the subclass's override rather than the abstract class's public method keeps the "Bitwig wins" order and
  suppresses the warning for answered keys.

## Notes for device authors (observed on 6.1.7; could go in the framework README)

- Items with an empty help id show no callout, unless their field `0x2932` is non-zero. That field looks like the
  item's place or ordering in the help view: stock EQ-5's band 3 Type chooser has an empty id and `0x2932 = 4`, and
  still gets a callout. Setting it to 0 removes the callout.
- Bitwig appends `[various options]` to the title of every choice parameter regardless of how many options it has
  (help view class `com.bitwig.flt.widget.core.device.CyJ`), `[toggle]` to booleans and `[trigger]` to triggers. Help
  files can't change those suffixes.
- The layout of the help view (where callouts and lines go) is computed by Bitwig (`CyJ`) from the items' positions.
  The device file doesn't control it.

## How it was tested (prototype, 6.1.7 macOS)

1. A custom device (`Sym EQ-5`) in a framework folder, with panel items using `sym-eq5.*` help ids, and
   `Sym EQ-5.help.properties` next to it.
2. With the mod loaded, the help view shows the file's titles and descriptions for every `sym-eq5.*` item. Stock ids
   on the same device (`eq-5.amount`, `device_general.out`, ...) still show Bitwig's texts.
3. Not tested yet: live reload (edit the help file with Bitwig running, then reopen the help view), and the
   behaviour with the help file removed (expected: raw keys, as before).
