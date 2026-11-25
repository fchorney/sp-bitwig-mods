# Mixer: Layout Abort Recovery

> **Built against**: Bitwig 6.1.7 (macOS, universal)
>
> **Will map to**: Bitwig 6.1.7 on macOS only, for now (see [Versions](#versions))
>
> **Mod ID**: bugfix-layout-abort-recovery
>
> **Prerequisites**: None

## What does this do?

Bitwig lays out a window in passes, and gives up after 1000 of them. When it gives up it throws
away its list of widgets still waiting for layout, and those widgets are never laid out or
drawn again. The window stops updating and looks frozen. Bitwig's log shows
`Maximum layout iteration count reached`.

This happens most easily in a big Mix view (around 200 or more mixer strips).

**Mixer: Layout Abort Recovery** keeps a copy of that list before Bitwig throws it away, and
queues the widgets for layout again on the next event, which gives the layout another 1000
passes. In the case it was written for, one retry is enough and the Mix view opens normally.

If a layout never settles, the mod stops retrying after 20 rounds, and Bitwig behaves as it
does without the mod.

## How do you use it?

No setup. Turn it on in Patchbay and use Bitwig as usual.

Each abort, retry and recovery is written to Bitwig's log under the mod's name. From a real
session, opening a Mix view that froze stock Bitwig 6.1.7 every time:

```text
[bugfix-layout-abort-recovery] layout aborted after 1000 passes, round 1, 1 roots: {aDs=1}, 0 ms into the episode
[bugfix-layout-abort-recovery] queued 1 of 1 roots again
[bugfix-layout-abort-recovery] layout recovered after 1 aborted round(s), 94 ms
```

## Tested

| Date | Bitwig | Patchbay | Result |
|---|---|---|---|
| 2026-11-25 | 6.1.7 macOS | BML 0.36.0-dev.17 | A Mix view that froze on every open opened and drew. 3 aborts in 30 s, each recovered after one retry (3,095 ms during project load, then 69 ms and 94 ms). The give-up-after-20-rounds path has not been seen. |

## Versions

Bitwig renames its internal classes in every release. Patchbay translates a mod's names to other
Bitwig versions only from its reference builds (6.1.3 and 6.1.4). This mod is written against
6.1.7 on macOS, so it loads on that exact build and is shown as not available on any other until
it is ported.
