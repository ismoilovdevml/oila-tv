#!/usr/bin/env bash
# Build tools/fapi/build/fapi.jar (a dex jar for app_process).
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
JAR=$SDK/platforms/android-35/android.jar
rm -rf build && mkdir -p build
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -d build Fapi.java
"$SDK/build-tools/35.0.0/d8" --min-api 28 --lib "$JAR" --output build build/Fapi.class
(cd build && zip -q fapi.jar classes.dex)
echo "built tools/fapi/build/fapi.jar"
