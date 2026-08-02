#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.1"
PLATFORM="$SDK/platforms/android-35/android.jar"
KOTLIN_LIB="/opt/homebrew/opt/kotlin/libexec/lib"
STDLIB="$KOTLIN_LIB/kotlin-stdlib.jar"

OUT=build
APK="$OUT/u30p-launcher.apk"
KS="$OUT/debug.keystore"

# Keystore'u derlemeler arasi koru; her yeniden imzalamada paket imzasi
# degisirse `adb install -r` reddeder.
if [ -f "$KS" ]; then
    mkdir -p "$OUT/.keep" && cp "$KS" "$OUT/.keep/debug.keystore"
fi
rm -rf "$OUT/classes" "$OUT/dex" "$OUT/base.apk" "$APK"
mkdir -p "$OUT/classes" "$OUT/dex"
if [ -f "$OUT/.keep/debug.keystore" ]; then
    cp "$OUT/.keep/debug.keystore" "$KS"
fi

echo "==> aapt2 compile (kaynaklar)"
mkdir -p "$OUT/gen"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

echo "==> aapt2 link (R uretimi)"
"$BT/aapt2" link \
    --manifest AndroidManifest.xml \
    -I "$PLATFORM" \
    --min-sdk-version 33 --target-sdk-version 35 \
    --java "$OUT/gen" \
    -o "$OUT/base.apk" \
    "$OUT/res.zip"

echo "==> javac (R sinifi)"
# R.java Java'dir; kotlinc derleyemez. Once javac ile derleyip Kotlin'e
# classpath olarak veriyoruz. R yalnizca int sabitleri icerir, android.jar'a
# ihtiyaci yok; --release ile eski bytecode uretiyoruz ki d8 kabul etsin.
find "$OUT/gen" -name 'R.java' > "$OUT/rjava.txt"
javac -nowarn --release 11 -d "$OUT/classes" @"$OUT/rjava.txt"

echo "==> kotlinc"
find src -name '*.kt' > "$OUT/sources.txt"
kotlinc -nowarn -jvm-target 11 -classpath "$PLATFORM:$OUT/classes" \
        -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> R8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 \
     --release --min-api 33 \
     --lib "$PLATFORM" \
     --pg-conf proguard.txt \
     --output "$OUT/dex" \
     @"$OUT/classes.txt" "$STDLIB"

echo "==> add dex"
(cd "$OUT/dex" && zip -q -X "../base.apk" classes*.dex)

echo "==> zipalign"
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$APK"

if [ ! -f "$KS" ]; then
    echo "==> keystore"
    keytool -genkeypair -keystore "$KS" -alias u30p -storepass android \
            -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=U30P,O=kaandikec,C=TR" >/dev/null 2>&1
    mkdir -p "$OUT/.keep" && cp "$KS" "$OUT/.keep/debug.keystore"
fi

echo "==> sign"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
                --min-sdk-version 33 "$APK"

echo "==> ok: $APK ($(du -h "$APK" | cut -f1), $(stat -f%z "$APK") bayt)"
