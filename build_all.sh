#!/bin/bash
set -e

rm -rf fabric/build/libs forge/build/libs build/release
mkdir -p build/release

GRADLE_OPTS="${GRADLE_OPTS:---offline -Dnet.minecraftforge.gradle.check.certs=false}"

echo "=== 1/4: Building Fabric 1.20.1 ==="
./gradlew :fabric:build -Pminecraft_version=1.20.1 $GRADLE_OPTS
cp fabric/build/libs/EMTRA-fabric-*.jar build/release/

echo "=== 2/4: Building Fabric 1.20.4 ==="
./gradlew :fabric:build -Pminecraft_version=1.20.4 $GRADLE_OPTS
cp fabric/build/libs/EMTRA-fabric-*.jar build/release/

echo "=== 3/4: Building Forge 1.20.1 ==="
./gradlew :forge:build -Pminecraft_version=1.20.1 $GRADLE_OPTS
cp forge/build/libs/EMTRA-forge-*.jar build/release/

echo "=== 4/4: Building Forge 1.20.4 ==="
./gradlew :forge:build -Pminecraft_version=1.20.4 $GRADLE_OPTS
cp forge/build/libs/EMTRA-forge-*.jar build/release/

rm -f build/release/*-sources.jar

echo "========================================="
echo "=== ALL JARS SUCCESSFULLY GENERATED! ==="
echo "========================================="
ls -lh build/release/
