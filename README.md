# MTR Alternate Platforms (Minecraft Transit Railway Addon)

A lightweight addon mod for **Minecraft Transit Railway (MTR 4.x)** that enables trains to automatically divert to alternate platforms when their primary scheduled platform is occupied or blocked by another vehicle/signal.

## Features
- **Dynamic Platform Diversion**: If an incoming platform track is blocked by a stopped or departing train, approaching trains smoothly divert to an available alternate platform without stopping or ping-ponging.
- **In-Game GUI Integration**: Configurable directly within MTR's dashboard route editor. Simply click on any stop in your route to select one or more alternate platforms from that station.
- **Standalone Addon**: Works alongside official MTR without requiring patched MTR or TSC builds.
- **Multi-Loader & Multi-Version**: Supports **Fabric** and **Forge** on Minecraft **1.20.1** and **1.20.4**.

---

## Downloads / Builds

All prebuilt jars are placed in `build/release/`:
- `MTR-Alternate-Platforms-fabric-1.0.0+1.20.1.jar`
- `MTR-Alternate-Platforms-fabric-1.0.0+1.20.4.jar`
- `MTR-Alternate-Platforms-forge-1.0.0+1.20.1.jar`
- `MTR-Alternate-Platforms-forge-1.0.0+1.20.4.jar`

---

## Building from Source

To build all 4 jars at once:
```bash
./build_all.sh
```

Or build individual loaders and Minecraft versions:
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

---

## License
MIT License.
