#!/system/bin/sh
# UFI band-button controller (2.4/5GHz key = gpio-keys KEY_HOME, scancode 102).
#
# - Boot: force com.kaandikec.u30plauncher as the default HOME launcher and front.
# - Runtime: count rapid presses of the band button (0.45s window between
#   presses) and run the action mapped to that click-count. The mapping is
#   configured by the UFI Button app; the daemon reads its config live.
#
# Actions: launcher_toggle | dpi_toggle | dpi_set | open_app | kill_bg | none
# Fully reversible: remove the module + reboot.

U30P="com.kaandikec.u30plauncher/.LauncherActivity"
ZTE="com.zte.mifavor.ufi.home/.HomeMainActivity"
APP="com.kaandikec.ufibutton/.MainActivity"
STATE="/data/adb/ufi_launcher_state"
DPI_STATE="/data/adb/ufi_button_dpi"
CFG="/data/data/com.kaandikec.ufibutton/files/ufi_button.conf"

# --- provisioning baseline (applied every boot; edit to taste) ---
BOOT_DPI="120"           # screen density forced at boot
SCREEN_TIMEOUT="600000"  # screen-off timeout in ms (10 min)

# read a value from the app config; $2 = default if missing
getcfg() {
    v=""
    if [ -f "$CFG" ]; then
        v=$(grep "^$1=" "$CFG" 2>/dev/null | head -1 | cut -d= -f2- | tr -d ' \r')
    fi
    [ -z "$v" ] && v="$2"
    echo "$v"
}

# default action for a click-count when the app hasn't configured it yet
default_action() {
    case "$1" in
        1) echo launcher_toggle ;;
        3) echo dpi_toggle ;;
        6) echo open_app ;;
        *) echo none ;;
    esac
}

# toggle default HOME between the u30p launcher and the stock ZTE launcher
toggle_launcher() {
    if [ "$(cat "$STATE" 2>/dev/null)" = "u30p" ]; then
        # ZTE boot'ta kapatiliyor; ona gecmek icin once etkinlestir
        pm enable com.zte.mifavor.ufi.home >/dev/null 2>&1
        cmd package set-home-activity "$ZTE" >/dev/null 2>&1
        am start -n "$ZTE" >/dev/null 2>&1
        echo zte > "$STATE"
    else
        cmd package set-home-activity "$U30P" >/dev/null 2>&1
        am start -n "$U30P" >/dev/null 2>&1
        echo u30p > "$STATE"
        pm disable-user --user 0 com.zte.mifavor.ufi.home >/dev/null 2>&1
    fi
}

toggle_dpi() {
    val=$(getcfg dpi_value 100)
    if [ "$(cat "$DPI_STATE" 2>/dev/null)" = "on" ]; then
        wm density reset >/dev/null 2>&1
        echo off > "$DPI_STATE"
    else
        wm density "$val" >/dev/null 2>&1
        echo on > "$DPI_STATE"
    fi
}

dispatch() {
    n="$1"
    act=$(getcfg "click$n" "$(default_action "$n")")
    case "$act" in
        launcher_toggle) toggle_launcher ;;
        dpi_toggle)      toggle_dpi ;;
        dpi_set)         wm density "$(getcfg dpi_value 100)" >/dev/null 2>&1 ;;
        open_app)
            pkg=$(getcfg "click${n}_pkg" "")
            if [ -n "$pkg" ]; then
                monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
            else
                am start -n "$APP" >/dev/null 2>&1
            fi
            ;;
        kill_bg)
            sh /data/adb/modules/ufi_default_launcher/ufi_clean.sh >/dev/null 2>&1
            ;;
        *) : ;;
    esac
}

(
    # wait for boot to complete
    i=0
    while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 75 ]; do
        sleep 2; i=$((i + 1))
    done
    sleep 5

    # --- device provisioning (idempotent, every boot) ---
    # mark setup complete so the setup wizard (com.android.provision) stops
    # grabbing the HOME role, then keep it out of the way.
    settings put global device_provisioned 1 >/dev/null 2>&1
    settings put secure user_setup_complete 1 >/dev/null 2>&1
    pm disable-user com.android.provision >/dev/null 2>&1
    # display / power baseline for a battery-optimised, always-plugged router
    wm density "$BOOT_DPI" >/dev/null 2>&1
    settings put system screen_off_timeout "$SCREEN_TIMEOUT" >/dev/null 2>&1
    settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1

    # Stock ZTE launcher acilista home task'ini kapip u30p'yi eziyor; kapatilmali.
    # (Asagidaki 4 kez set-home-activity denemesi tek basina yetmiyor: HOME
    # tercihi bizde olsa bile task ZTE'de kaliyor.)
    pm disable-user --user 0 com.zte.mifavor.ufi.home >/dev/null 2>&1

    cmd package install-existing com.kaandikec.u30plauncher >/dev/null 2>&1
    pm enable com.kaandikec.u30plauncher >/dev/null 2>&1
    cmd role add-role-holder android.app.role.HOME com.kaandikec.u30plauncher >/dev/null 2>&1

    # assert u30p default+front a few times during boot settle (the stock
    # ZTE launcher tends to pull itself forward), THEN start listening so
    # boot-time key noise cannot trigger a spurious action.
    k=0
    while [ "$k" -lt 4 ]; do
        cmd package set-home-activity "$U30P" >/dev/null 2>&1
        am start -n "$U30P" >/dev/null 2>&1
        echo u30p > "$STATE"
        sleep 4; k=$((k + 1))
    done

    # multi-click daemon
    while true; do
        getevent -lq /dev/input/event0 2>/dev/null \
        | while read -r a b c; do
              [ "$b" = "KEY_HOME" ] && [ "$c" = "DOWN" ] && echo X
          done \
        | while :; do
              read -r first || break
              n=1
              while read -r -t 0.45 more; do n=$((n + 1)); done
              dispatch "$n"
          done
        sleep 2
    done
) &
