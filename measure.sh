#!/usr/bin/env bash
# Spec §4 performans butcesini cihazda olcer.
set -uo pipefail

A="$HOME/platform-tools/adb"
D="${U30P_SERIAL:-192.168.0.1:55555}"
PKG=com.kaandikec.u30plauncher
adbd() { "$A" -s "$D" "$@"; }

echo "== APK boyutu"
stat -f%z build/u30p-launcher.apk 2>/dev/null || stat -c%s build/u30p-launcher.apk

echo
echo "== Soguk acilis"
adbd shell am force-stop $PKG
sleep 1
adbd shell input keyevent KEYCODE_WAKEUP >/dev/null
adbd shell am start -W -n $PKG/.LauncherActivity | grep -E 'TotalTime|WaitTime'

echo
echo "== RAM (ekran acik)"
sleep 6
adbd shell dumpsys meminfo $PKG | grep -E 'TOTAL PSS|TOTAL RSS' | head -2

echo
echo "== CPU (ekran acik, 10 sn ortalamasi)"
adbd shell top -b -n 2 -d 10 -m 40 -q | grep $PKG | tail -1 || echo "listede yok"

echo
echo "== Ekran kapali"
adbd shell input keyevent KEYCODE_SLEEP
sleep 12
adbd shell dumpsys meminfo $PKG | grep 'TOTAL PSS' | head -1
adbd shell top -b -n 2 -d 10 -m 40 -q | grep $PKG | tail -1 || echo "CPU: listede yok (%0)"
adbd shell input keyevent KEYCODE_WAKEUP >/dev/null
