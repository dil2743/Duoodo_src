#!/usr/bin/env bash
# Lists the permissions requested by an APK and fails if any reaches user data
# (storage, media, camera, microphone, contacts, location, phone, SMS, accounts).
# Usage: check-apk-privacy.sh <file.apk>   (requires Android SDK build-tools)
set -euo pipefail

apk="${1:?usage: $0 <file.apk>}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android/sdk}}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
[ -x "$aapt2" ] || { echo "aapt2 not found under $sdk/build-tools" >&2; exit 2; }

perms="$("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")"

echo "Permissions in $(basename "$apk"):"
echo "$perms" | sed 's/^/  /'

sensitive="$(echo "$perms" | grep -E '\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|READ_MEDIA_[A-Z_]+|CAMERA|RECORD_AUDIO|READ_CONTACTS|WRITE_CONTACTS|GET_ACCOUNTS|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|ACCESS_BACKGROUND_LOCATION|READ_PHONE_STATE|READ_PHONE_NUMBERS|READ_SMS|RECEIVE_SMS|READ_CALL_LOG)$' || true)"
if [ -n "$sensitive" ]; then
  echo "FAIL: permissions that reach user data found:" >&2
  echo "$sensitive" >&2
  exit 1
fi
echo "PASS: no access to storage, media, camera, microphone, contacts, location, phone or SMS."
