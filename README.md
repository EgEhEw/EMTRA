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
- Train loops play from the car's exact position, 1.5 blocks above the rail, instead of a block corner at rail level that jumped a block at a time. At a platform edge, sound physics no longer sees the source flip in and out of the platform blocks.
- With Sound Physics Remastered, the direct and reverb filters of moving sounds glide to each re-evaluated value over about 0.12 s instead of stepping, which clicked where the result varies (walls, tunnels, platforms).
- Vehicles advance by the exact frame time. MTR rounds each frame down to whole milliseconds and catches up with a one-tick jump once the lost time reaches a tick, which made trains visibly stutter at high frame rates. MTR's frame timing is adjusted without being cancelled, so other mods' hooks on it keep running (in 1.2.6 this stopped JCM's script clock: `Timing.delta`, `RateLimit` and everything built on them, such as pantographs searching for the catenary).
- Small rotations near a half turn are drawn exactly. Minecraft's rotation math snaps them to steps there, so the suspension pitch of a car on level track jumped between 0° and 0.04°.

## Client options

`config/emtra-client.properties` (created on first launch), also editable in game from the mod list (Forge: Mods > EMTRA > Config; Fabric: Mod Menu):

Rendering

- `renderInstancing` (default `true`): draws repeated MTR model parts that share a mesh, such as 3D rail segments, with one GPU-instanced draw call instead of one call per part. Translucent parts, GL4ES and setups without MTR's custom shader keep MTR's own path; any error disables the feature for the session.
- `renderRailCache` (default `true`): records the rail model placements MTR computes for each 3D rail and reuses them on later frames instead of recomputing rail curves every frame. Visibility is decided per rail where possible (only partly visible rails check their segments one by one), and only the segments in a section whose light changed re-read it, as soon as it changes. Rails are re-recorded every few seconds; flat textured rails are not cached.
- `renderCatenaryCache` (default `true`): for MSD (MTR Station Decoration) rigid catenary, captures the quads MSD draws once and keeps them as MTR optimized models on the GPU instead of rebuilding them vertex by vertex every frame. Light follows changes immediately and catenaries are re-captured every few seconds; nothing happens when MSD is not installed. Only MSD's own drawing goes through the cache: scripts and other mods that read rigid catenary geometry through MSD (for example JCM pantograph scripts) still receive the real segments.
- `subdivideModels` (default `true`): splits MTR model faces along a one-block grid when a model is built, without changing how it looks. Shader packs bend the shadow map around the player per vertex, so a roof drawn as one 14-block face bent in the shadow map and shadowed itself up close. The split takes milliseconds per model and roughly triples the triangle count. Changes apply after a resource reload (F3+T).
- `renderStats` (default `false`): logs model, draw call, rail cache, catenary cache, lift model cache and sound filter counts every 10 seconds.
- Advanced SWAY (`advancedSway`, off on new installations) replaces MTR's vehicle oscillation and locks its sway slider until Advanced SWAY is turned off. Horizontal and vertical sway follow speed. Coaches sharing a Jacobs bogie move together at the joint and can sway independently at their outer ends. Trains also pitch toward the direction of travel under braking and settle after a full stop.
- `advancedSwayHorizontal`, `advancedSwayVertical`, and `advancedSwayBrakePitch` control each effect from 0% to 200%. At 100%, brake pitch is capped at 0.05°. Upgrading from `vehicleSway` and `trainStopSettle` preserves which effects were enabled.

Lift models are always reused: MTR builds a new lift model for every lift on every frame (also when FangSu replaces the model); EMTRA keeps one model per lift size instead. Start the game with `-Demtra.liftModelCache=false` to turn this off.

PIDS text uses Minecraft's world rendering buffers and keeps MTR's arrival refresh timing. Railway signs and station names can draw past the block entity render range when their chunks are loaded. Distant railway signs use separate depths for their background and lettering to prevent flickering.

Sound

- `trainMuffle` (default `false`, experimental): while riding a train, exterior sounds and the train's own motor, run, `airhigh` and `airzero` sounds get the full cabin filter. The riding train's other sounds get 15% of that effect. The filter fades in and out when boarding and alighting and follows the doors as they open and close.
- `trainMuffleVolume` (default `55`) and `trainMuffleTreble` (default `10`): volume and high frequencies left at full muffle, in percent.
- `trainMuffleDoorOpen` (default `75`): how much of the muffle open doors remove, in percent.
- `trainMuffleFadeMs` (default `700`) and `trainMuffleDoorFadeMs` (default `250`): fade times for boarding/alighting and for the doors.

## Native articulated vehicle models

Jacobs articulation parts modeled inside a bogie are detected automatically: in the native bogie slots, model parts whose names start a word with `artic` (`articularinside`, `articularoutside`, `Articulation_Outer`...) are split from the wheels when the pack loads. The wheels keep following the rail, and the articulation sits between the two swaying car ends at the shared joint. No change to the resource pack is needed; MTR's resource pack creator still sees the original bogie entries.

EMTRA detects shared bogies from adjacent carriages' lengths, coupling padding and bogie positions. For trim that rotates with the shared bogie's heading and follows the body displacement at that joint, list the trim as a separate model in the native MTR bogie slot. Put `"emtraBogieAttachment": true` in that model's properties JSON. For the trim to follow both carriages vertically, `emtraBogieAttachmentHalfLength` and `emtraBogieAttachmentHeight` in the same JSON describe the contact planes in blocks relative to the bogie anchor. They default to 0.5 and 1. Wheel models use the ordinary bogie slot with their own model properties. A bogie attachment at an unshared pivot renders at its original MTR pose.

## Optional JCM vehicle suspension API

Vehicle scripts on MTR 4.0.5 and JCM 2.3 can submit a suspension pose for their assigned carriages. EMTRA registers `com.egehew.emtra.api.VehicleSuspension` with JCM's class allowlist.

```js
var suspension = Packages.com.egehew.emtra.api.VehicleSuspension;

function render(ctx, state, vehicle) {
    var cars = ctx.getMyCars();
    var bodies = new Array(cars.length * 3).fill(0);
    var displacement = vehicle.getSpeedKmh() > 0 ? Math.sin(Date.now() / 200) * 0.004 : 0;
    for (var i = 0; i < cars.length; i++) {
        bodies[i * 3 + 1] = displacement;
    }
    suspension.submit(ctx, bodies, null, true);
}
```

`submit(ctx, bodies, bogieAttachments, replaceStockOscillation)` takes world-space XYZ translations in blocks. Arrays follow `ctx.getMyCars()` order. Each carriage has one body triple and two attachment triples in bogie order. A null array supplies zero offsets. Body offsets also move MTR's doors, floors, riding position and JCM car render anchor. Attachment offsets move JCM bogie render anchors and keep wheel positions on the track. The final flag replaces the built-in sway for the submitted carriages while the sample is active. An articulated unit uses one sway source, and the native brake pitch can still run.

With Advanced SWAY, the car poses JCM gives every vehicle script (`train.lastCarPosition`, `train.lastCarRotation` and the door checks) include the body sway drawn in the last frame, so script parts aimed at the world, such as pantographs reaching for the catenary, stay on target while the body moves.

Call `submit` during each render callback. Samples expire after 250 ms. Invalid contexts, array lengths or non-finite values return `false` and leave the previous sample in effect.

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
