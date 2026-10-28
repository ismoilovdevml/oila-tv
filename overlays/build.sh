#!/usr/bin/env bash
# Build every resource overlay under overlays/<name>/ into overlays/<name>/build/<name>.apk.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-35/android.jar
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
for d in */; do
  d=${d%/}; [ -f "$d/AndroidManifest.xml" ] || continue
  rm -rf "$d/build" && mkdir -p "$d/build"
  "$BT/aapt2" compile --dir "$d/res" -o "$d/build/res.zip"
  "$BT/aapt2" link -o "$d/build/u.apk" -I "$JAR" --manifest "$d/AndroidManifest.xml" \
      --min-sdk-version 28 --target-sdk-version 28 "$d/build/res.zip"
  "$BT/zipalign" -f 4 "$d/build/u.apk" "$d/build/a.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
      --out "$d/build/$d.apk" "$d/build/a.apk"
  echo "built $d/build/$d.apk"
done
