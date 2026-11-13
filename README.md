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
