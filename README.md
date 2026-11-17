# EMTRA (EgEhEs MTR Addon)

EMTRA (EgEhEs MTR Addon) is an addon mod for Minecraft Transit Railway (MTR 4.x) that introduces dynamic alternate platform routing, real-time arrival tracking for Passenger Information Display Systems (PIDS), and improved traffic management for complex rail networks.

## Overview

In busy railway and transit networks built with Minecraft Transit Railway, stations with shared lines or high train frequencies can experience congestion when multiple trains arrive at the same station simultaneously. EMTRA allows railway operators to configure alternate platforms for any station stop. When an approaching train detects that its primary scheduled platform is occupied or signal-blocked, it dynamically reroutes to an available alternate platform.

## Features

- Dynamic Alternate Platform Diversion: Approaching trains inspect track reservations and real-time vehicle positions. If the primary platform or switch track is blocked, the train smoothly recalculates its path to a clear alternate platform.
- PIDS arrival filtering preserves other platforms' and sidings' results and applies the requested count after choosing the actual platform.
- Signal Conflict & Phantom Braking Resolution: Clamps signal checking to the platform stopping point when entering an empty platform, preventing false emergency stops caused by departing trains on adjacent platforms or exit throat switches.
- 4-Node Lookahead Horizon & Platform Calculation: Traverses at least 4 nodes back in the rail graph and ignores passed/current platform boundaries to ensure accurate alternate platform prediction and diversion.

## MAGIC MTR compatibility

When MAGIC MTR is installed, EMTRA takes over its alternate platform routing and arrival filtering automatically. Use MAGIC's alternate platform menu: EMTRA hides its own menu and uses MAGIC's saved selections, including all compatible platforms at a station. MAGIC's global and per-route settings still apply. Regenerate depot paths after changing selections. Without MAGIC, EMTRA's own menu and saved selections remain available.

MAGIC's flexible timetables remain active. A train scheduled to skip a platform can divert around an occupied platform and continue through the alternative, retaining its original dwell time instead of gaining a passenger stop. Other inserted stops also retain the service's timetable. Compatibility targets MAGIC 1.5.1's `AlternativePlatformRegistry` API; MAGIC is optional and is not bundled.

## Train fixes and sound

Always active on the client:

- Train doors keep their animation after a vehicle model is reloaded (fixes doors drawn closed while the doorway is open, until F3+T).
- MTR vehicle sounds: looping BVE sounds (motor, run, flange, noise...) change volume and pitch smoothly, so a motor output that flickers from frame to frame no longer stops and restarts loops (clicks). Loop volume is capped at 1, which only limits the audible range to the one the pack's `sounds.json` gives (BVE packs with `MotorVolumeMultiply = 5` were heard 640 blocks away). One-shot sounds play once per event instead of once per scheduled sound.
- Train sounds follow the air: with a clear line to the car body (rail level up to 2 blocks above, so a train beside a raised platform is heard) they are unchanged; otherwise the sound has to travel through tunnels, stations and stairwells. A train around a tunnel bend or behind a platform wall is heard from the direction of the opening it comes through, as far away as the path is long, quieter and duller the longer the detour; a train under solid ground is not heard at all. Thin walls (1-2 blocks) still let some sound through.
- Cars that are not drawn (behind a bend or a wall) keep updating their sounds within hearing range, instead of freezing at their last volume and position.
- Sound Physics Perfected compatibility: vehicle sounds (MTR, JCM and FangSu scripts) are not replaced by SPP's ray-traced copies, which were updated twice a second and could not follow BVE sounds (stacked, jumping, leaking sounds); SPP's reverb still applies to them, combined with EMTRA's filters. SPP's permeation count no longer stops at its limit (30 blocks of ground counted as 3).

## Client options

`config/emtra-client.properties` (created on first launch), also editable in game from the mod list (Forge: Mods > EMTRA > Config; Fabric: Mod Menu):

Rendering

- `renderInstancing` (default `true`): draws repeated MTR model parts that share a mesh, such as 3D rail segments, with one GPU-instanced draw call instead of one call per part. Translucent parts, GL4ES and setups without MTR's custom shader keep MTR's own path; any error disables the feature for the session.
- `renderRailCache` (default `true`): records the rail model placements MTR computes for each 3D rail and reuses them on later frames instead of recomputing rail curves every frame. Visibility is decided per rail where possible (only partly visible rails check their segments one by one), and only the segments in a section whose light changed re-read it, as soon as it changes. Rails are re-recorded every few seconds; flat textured rails are not cached.
- `renderCatenaryCache` (default `true`): for MSD (MTR Station Decoration) rigid catenary, captures the quads MSD draws once and keeps them as MTR optimized models on the GPU instead of rebuilding them vertex by vertex every frame. Light follows changes immediately and catenaries are re-captured every few seconds; nothing happens when MSD is not installed.
- `renderStats` (default `false`): logs model, draw call, rail cache, catenary cache, lift model cache and sound filter counts every 10 seconds.

Lift models are always reused: MTR builds a new lift model for every lift on every frame (also when FangSu replaces the model); EMTRA keeps one model per lift size instead. Start the game with `-Demtra.liftModelCache=false` to turn this off.

Sound

- `trainMuffle` (default `false`, experimental): while riding a train, everything outside and the train's own motor and run sounds are muffled as if heard through the car body; its doors and other sounds stay clear. The muffle fades in and out when boarding and alighting and follows the doors as they open and close.
- `trainMuffleVolume` (default `55`) and `trainMuffleTreble` (default `10`): volume and high frequencies left at full muffle, in percent.
- `trainMuffleDoorOpen` (default `75`): how much of the muffle open doors remove, in percent.
- `trainMuffleFadeMs` (default `700`) and `trainMuffleDoorFadeMs` (default `250`): fade times for boarding/alighting and for the doors.

## Supported Versions

- Minecraft 1.20.1: Fabric, Forge
- Minecraft 1.20.4: Fabric, Forge
- Dependency: Minecraft Transit Railway (MTR) 4.0.0 or higher

## Building from Source

Prerequisites:
- Java 17 JDK
- Internet connection for initial dependency resolution (or offline gradle cache)

To build all loader and version variants at once:
```bash
./build_all.sh
```

To build individual variants:

```bash
# Fabric 1.20.1
./gradlew :fabric:build -Pminecraft_version=1.20.1

# Fabric 1.20.4
./gradlew :fabric:build -Pminecraft_version=1.20.4

# Forge 1.20.1
./gradlew :forge:build -Pminecraft_version=1.20.1

# Forge 1.20.4
./gradlew :forge:build -Pminecraft_version=1.20.4
```

Compiled jar files are placed in the `build/release/` directory.

Headless regression checks can run with the cached Gradle dependencies even when Gradle cannot start:
```bash
python3 tests/run_checks.py
python3 tests/run_checks.py --magic-jar /path/to/MAGIC-1.5.1-1.20.1.jar
```
These compile the common/server code, exercise MTR's arrival consumer and routing helpers, and optionally check the compatibility transformations against MAGIC's actual bytecode. They do not replace a Fabric/Forge build or an in-game test.

For a Forge 1.20.1 test JAR when Gradle cannot open its local lock sockets, use the cached-dependency builder:
```bash
python3 tools/build_offline_forge.py --jdk /path/to/java17 --mod-version 1.0.4-test1
```
It compiles all common and Forge sources, packages the loader metadata and resources, and checks that no Minecraft member references require ForgeGradle reobfuscation. It requires the cached MTR 4.0.5 and Forge 47.3.0 development dependencies. Normal release builds should still use Gradle.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
