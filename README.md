# EMTRA (EgEhEs MTR Addon)

EMTRA (EgEhEs MTR Addon) is an addon mod for Minecraft Transit Railway (MTR 4.x) that introduces dynamic alternate platform routing, real-time arrival tracking for Passenger Information Display Systems (PIDS), and improved traffic management for complex rail networks.

## Overview

In busy railway and transit networks built with Minecraft Transit Railway, stations with shared lines or high train frequencies can experience congestion when multiple trains arrive at the same station simultaneously. EMTRA allows railway operators to configure alternate platforms for any station stop. When an approaching train detects that its primary scheduled platform is occupied or signal-blocked, it dynamically reroutes to an available alternate platform.

## Features

- Dynamic Alternate Platform Diversion: Approaching trains inspect track reservations and real-time vehicle positions. If the primary platform or switch track is blocked, the train smoothly recalculates its path to a clear alternate platform.

## Supported Versions

- Minecraft 1.20.1: Fabric, Forge
- Minecraft 1.20.4: Fabric, Forge
- Dependency: Minecraft Transit Railway (MTR) 4.0.0 or higher

## Installation

1. Install Minecraft Forge or Fabric Loader for your target Minecraft version.
2. Install the matching version of Minecraft Transit Railway (MTR 4.x).
3. Download the appropriate EMTRA jar file for your mod loader and Minecraft version from the Releases page.
4. Place the EMTRA jar into your Minecraft instance `mods` directory.

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

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the LICENSE file for details.
