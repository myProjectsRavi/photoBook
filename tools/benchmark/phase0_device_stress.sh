#!/usr/bin/env bash
set -euo pipefail

# Phase-0 destructive-ish stress harness for a dedicated benchmark/test device.
# It does not modify PhotoBook production code. It repeatedly exercises Android
# lifecycle pressure, process death, permission revoke/regrant, and low-memory
# callbacks while collecting crash/ANR/memory evidence.

PACKAGE="${PACKAGE:-com.photobook.app}"
ACTIVITY="${ACTIVITY:-com.photobook.app/.MainActivity}"
ITERATIONS="${ITERATIONS:-20}"
OUT_DIR="${OUT_DIR:-build/reports/phase0/device-stress}"

mkdir -p "$OUT_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 2
fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=( -s "$ANDROID_SERIAL" )
fi

"${ADB[@]}" get-state >/dev/null
SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
RAM_KB="$("${ADB[@]}" shell cat /proc/meminfo | tr -d '\r' | awk '/MemTotal/ {print $2; exit}')"

cat > "$OUT_DIR/device.txt" <<EOF
package=$PACKAGE
activity=$ACTIVITY
sdk=$SDK
model=$MODEL
ram_kb=$RAM_KB
iterations=$ITERATIONS
EOF

"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true

launch_app() {
  "${ADB[@]}" shell am start -W -n "$ACTIVITY" >/dev/null
  sleep 1
}

for ((i = 1; i <= ITERATIONS; i++)); do
  echo "stress iteration $i/$ITERATIONS"
  "${ADB[@]}" shell am force-stop "$PACKAGE"
  launch_app

  # Exercise foreground/background restoration.
  "${ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 0.2
  launch_app

  # Ask Android to deliver progressively stronger memory pressure.
  "${ADB[@]}" shell am send-trim-memory "$PACKAGE" RUNNING_LOW >/dev/null 2>&1 || true
  "${ADB[@]}" shell am send-trim-memory "$PACKAGE" RUNNING_CRITICAL >/dev/null 2>&1 || true

  # Simulate process death while the task may still exist, then recover.
  "${ADB[@]}" shell am kill "$PACKAGE" >/dev/null 2>&1 || true
  launch_app

done

# Permission volatility is critical on modern Android. Revoke and restore only
# permissions the platform exposes on this API level. The app must recover
# without a crash; the user-visible permission flow itself remains unchanged.
if (( SDK >= 33 )); then
  "${ADB[@]}" shell pm revoke "$PACKAGE" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 || true
  launch_app
  "${ADB[@]}" shell pm grant "$PACKAGE" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 || true
else
  "${ADB[@]}" shell pm revoke "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
  launch_app
  "${ADB[@]}" shell pm grant "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
fi

"${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$OUT_DIR/meminfo.txt" || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/gfxinfo.txt" || true
"${ADB[@]}" logcat -d -b crash > "$OUT_DIR/crash-buffer.txt" || true
"${ADB[@]}" logcat -d > "$OUT_DIR/logcat.txt" || true

if (( SDK >= 30 )); then
  "${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE" > "$OUT_DIR/exit-info.txt" || true
fi

if grep -Eiq 'FATAL EXCEPTION|ANR in com\.photobook\.app|OutOfMemoryError' "$OUT_DIR/crash-buffer.txt" "$OUT_DIR/logcat.txt"; then
  echo "FAIL: crash/ANR/OOM evidence found; inspect $OUT_DIR" >&2
  exit 1
fi

echo "PASS: no crash/ANR/OOM signature observed in Phase-0 lifecycle stress run"
echo "reports: $OUT_DIR"
