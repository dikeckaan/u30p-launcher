#!/usr/bin/env bash
set -euo pipefail
OUT=build/test
rm -rf "$OUT"; mkdir -p "$OUT"
find src/com/kaandikec/u30plauncher/core test -name '*.kt' > "$OUT/sources.txt"
kotlinc -nowarn -d "$OUT/test.jar" @"$OUT/sources.txt"
java -cp "$OUT/test.jar:/opt/homebrew/opt/kotlin/libexec/lib/kotlin-stdlib.jar" \
     com.kaandikec.u30plauncher.test.TestMainKt
