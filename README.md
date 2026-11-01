# MTR Signal Peripheral (addon for Minecraft Transit Railway + CC:Tweaked)

NeoForge 1.21.1 addon. Every MTR signal (2/3/4-aspect lights and semaphores, single and
double sided) becomes a ComputerCraft peripheral of type `mtr_signal`.

## Get the jar

The result is one file: `build/libs/mtr_signal_cc-1.0.0.jar`. Drop it into `mods/` next to MTR and
CC:Tweaked, on the server **and** on clients (the client half reports MTR's aspect, see below).

**Option A: let GitHub build it (no setup)**
1. Create a new GitHub repo and push the contents of this folder to it.
2. Open the repo's **Actions** tab. The "Build jar" workflow runs on every push
   (or press *Run workflow*). When it is green, open the run and download the
   **mtr_signal_cc-jar** artifact: that zip contains the jar.
   The workflow pulls your MTR jar from `Spotsimon2011/mod_check` through Git LFS by itself.

**Option B: build locally** (needs Java 21 and internet)
1. Copy your MTR jar into `libs/` (name set by `mtr_jar` in `gradle.properties`).
2. `./gradlew build` (Windows: `gradlew.bat build`).

`neo_version` / `cc_version` in `gradle.properties` are starting points; bump to what you run.

## Lua API

```lua
local sig = peripheral.find("mtr_signal")
```

Sides are **1** and **2** (single sided signals only have side 1). Aspects are numbered from 0.

| Method | Returns / does |
|---|---|
| `getSignalType()` | `"light"` or `"semaphore"` |
| `isDoubleSided()` / `getSideCount()` | `true/false` / `1` or `2` |
| `getAspectCount()` | number of aspects this signal has |
| `getAspects()` | `{[0]="proceed",[1]="stop",...}` : the aspects you can set |
| `getInfo()` | block id, type, sides, aspects, position |
| `getStatus()` | `getInfo()` plus `sideStatus` list (one entry per side, see below) |
| `getSideStatus([side])` | status of one side |
| `getAspect([side])` / `getAspectName([side])` | what the side shows now, `nil` if unknown |
| `getMode([side])` | `"auto"` or `"manual"` |
| `setMode(mode [, side])` | `"auto"`/`"manual"`; no side = all sides |
| `setAspect(aspect [, side])` | number or name; **manual mode only** |

`side` is required on a double sided signal for everything except `setMode`.

Aspects per type:

| Type | Aspects |
|---|---|
| 2-aspect light, semaphore | 0 proceed, 1 stop |
| 3-aspect light | 0 proceed, 1 stop, 2 caution |
| 4-aspect light | 0 proceed, 1 stop, 2 caution, 3 double_caution |

Per-side status fields: `side`, `mode`, `aspectKnown`, `aspect`, `aspectName`,
`manualAspect` (manual only), `nativeKnown`, `nativeAspect`, `nativeAspectName`, `nativeAgeMs`.
`native*` is what MTR itself is choosing, even while you override it.

### Modes

* **manual**: the side freezes on what it currently shows, then follows `setAspect`.
* **auto**: `setAspect` throws `"Side N is in auto mode..."`. Switching manual -> auto drops
  the override, and the side immediately shows MTR's own aspect (MTR's logic never stops
  running underneath the override, so there is no stale state).

```lua
sig.setMode("manual", 2)
sig.setAspect("stop", 2)
print(sig.getAspectName(2))   --> stop
sig.setMode("auto", 2)        -- back to MTR
```

## In-game command (op level 2)

```
/mtrsignal info   <pos>
/mtrsignal mode   <pos> <auto|manual> [side]
/mtrsignal aspect <pos> <aspect> [side]
```

## How it works (and the one big limitation)

MTR 4 does not compute signal aspects on the server. `BlockSignalBase.BlockEntityBase.getActualAspect`
is called from the **client renderer**, using client-side rail occupancy data; the server only
receives a redstone level. So:

* The addon Mixes into that block entity. The override (manual flag + aspect per side) is saved in the
  block entity NBT and synced by MTR's existing block entity sync. On the client, `getActualAspect` runs
  normally and its result is replaced only if the side is manual.
* Because the override goes through `getActualAspect`, MTR's redstone output (if enabled on the signal)
  follows the manual aspect too.
* To let the server/peripheral read MTR's native aspect, clients send it to the server when it changes
  (and every 5 s). In **auto** mode `getAspect()` therefore returns `nil` (`aspectKnown=false`) when no
  player is near enough to render the signal. **Manual** mode and `setMode/setAspect` work without anyone nearby.
  This is the same constraint MTR's own redstone output has.

## Status

Written against the decompiled classes of `MTR-neoforge-4.1.0-beta.2-1.21.1-signal-facing-fix`
and CC:Tweaked 1.120.0 docs. Not compiled or run in-game yet. Please try it and report compile errors.
