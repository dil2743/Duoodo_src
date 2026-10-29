#!/usr/bin/env bash
# Tiny UI driver for emulator testing. Usage: source scripts/adbui.sh; tap emulator-5554 "Send"
ADB=${ADB:-$HOME/android/sdk/platform-tools/adb}

dump() { timeout 20 $ADB -s "$1" shell "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && cat /sdcard/ui.xml"; }

# Center of the first node whose text is exactly $2.
center() {
  dump "$1" | grep -oE "text=\"$2\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 \
    | grep -oE 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

tap() {
  local xy; xy=$(center "$1" "$2")
  [ -z "$xy" ] && { echo "tap: '$2' not found on $1" >&2; return 1; }
  $ADB -s "$1" shell input tap $xy
}

# Wait until text starting with $2 is on screen (timeout $3 seconds, default 60).
waitfor() {
  local end=$(( $(date +%s) + ${3:-60} ))
  while [ "$(date +%s)" -lt "$end" ]; do
    dump "$1" | grep -q "text=\"$2" && return 0
    sleep 2
  done
  echo "waitfor: '$2' not seen on $1" >&2; return 1
}

textof() { dump "$1" | grep -oE "text=\"$2[^\"]*\"" | head -1 | cut -d'"' -f2; }

shot() { $ADB -s "$1" exec-out screencap -p > "$2"; }
