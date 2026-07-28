#!/usr/bin/env bash
# Cihaz yardimcisi: kurulum, baslatma, ekran goruntusu.
#
# adb-over-wifi duserse `./dev.sh revive` ttyd (port 1146) uzerinden adbd'yi
# geri baslatir — bu kanal adbd'ye bagli olmadigi icin emniyet agidir.
set -euo pipefail

A="$HOME/platform-tools/adb"
D="${U30P_SERIAL:-192.168.0.1:5555}"
PKG=com.kaandikec.u30plauncher
ACT="$PKG/.LauncherActivity"
SHOT_DIR="${U30P_SHOTS:-/tmp/u30p}"

adbd() { "$A" -s "$D" "$@"; }

case "${1:-help}" in
  install)
    ./build.sh
    adbd install -r build/u30p-launcher.apk | tail -1
    adbd shell pm grant $PKG android.permission.READ_PHONE_STATE || true
    adbd shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
    ;;

  start)
    # Mevcut UFI launcher kilit ekrani ustte kalabiliyor; once uzun basip acar.
    adbd shell input keyevent KEYCODE_WAKEUP
    sleep 1
    adbd shell input swipe 120 120 120 120 1200 || true
    sleep 1
    adbd shell am start -n "$ACT" >/dev/null
    ;;

  shot)
    mkdir -p "$SHOT_DIR"
    name="${2:-shot}"
    adbd exec-out screencap -p > "$SHOT_DIR/$name.png"
    echo "$SHOT_DIR/$name.png"
    ;;

  go)  # install + start + shot
    "$0" install && "$0" start && sleep 2 && "$0" shot "${2:-shot}"
    ;;

  top)
    adbd shell dumpsys activity activities | grep -m1 topResumedActivity
    ;;

  logs)
    adbd logcat -d -s U30P AndroidRuntime | tail -40
    ;;

  revive)
    # adbd oldugunde ttyd uzerinden geri getir
    python3 "${U30P_TTYD:-$HOME/Desktop/u30p-launcher/tools}/dsh.py" \
      'setprop service.adb.tcp.port 5555; setprop persist.service.adb.tcp.port 5555; setprop ctl.start adbd; sleep 3; getprop init.svc.adbd'
    "$A" connect "$D"
    ;;

  *)
    echo "kullanim: ./dev.sh {install|start|shot [ad]|go [ad]|top|logs|revive}"
    ;;
esac
