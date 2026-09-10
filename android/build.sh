#!/bin/bash
# Napravi i instaliraj aplikaciju.
#   ./build.sh          napravi APK (skupljen)
#   ./build.sh install  napravi i posalji na povezan telefon
#   ./build.sh debug    brzi build bez skupljanja
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f local.properties ]; then
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

if [ "${1:-}" = "debug" ]; then
  ./gradlew assembleDebug
  APK=app/build/outputs/apk/debug/app-debug.apk
else
  # Release je skupljen R8-om. Komponente iz manifesta su u proguard-rules.pro:
  # bez toga ih R8 preimenuje, sistem ih ne nadje, i nista se ne desi.
  ./gradlew assembleRelease
  APK=app/build/outputs/apk/release/app-release.apk
fi

echo
echo "APK: $APK  ($(du -h "$APK" | cut -f1))"

if [ "${1:-}" = "install" ]; then
  if [ -z "$(adb devices | sed '1d' | grep -w device || true)" ]; then
    echo
    echo "Nema povezanog telefona. Ukljuci USB debugging pa:  adb devices"
    exit 1
  fi
  adb install -r "$APK"
  echo "Instalirano."
fi
