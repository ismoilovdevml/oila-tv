#!/usr/bin/env bash
# Build Oila TV launcher APK without Gradle: aapt2 link -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-35/android.jar
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 28 --target-sdk-version 28 "$OUT/res.zip"
# android.jar has no LambdaMetafactory: use anonymous classes, not lambdas.
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -cp "$JAR" \
    -d "$OUT/classes" $(find src -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/oila-tv.apk" "$OUT/aligned.apk"
ls -l "$OUT/oila-tv.apk"
