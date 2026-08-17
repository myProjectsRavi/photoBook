#!/usr/bin/env bash
set -euo pipefail

# Phase-3 scale/device certification runner.
#
# This script does not modify production source or user media. The benchmark test
# APK owns and seeds its isolated Pictures/PhotoBookBenchmark MediaStore corpus.
# Use REQUIRE_PHYSICAL=1 for release-grade physical-device evidence.

LIBRARY_SIZE="${LIBRARY_SIZE:-10000}"
STRESS_ITERATIONS="${STRESS_ITERATIONS:-12}"
REQUIRE_PHYSICAL="${REQUIRE_PHYSICAL:-0}"
OUT_DIR="${OUT_DIR:-build/reports/phase3/device-${LIBRARY_SIZE}}"
PACKAGE="com.photobook.app"

case "$LIBRARY_SIZE" in
  10000|50000|100000) ;;
  *)
    echo "LIBRARY_SIZE must be one of: 10000, 50000, 100000" >&2
    exit 2
    ;;
esac

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 2
fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=( -s "$ANDROID_SERIAL" )
fi

mkdir -p "$OUT_DIR"
"${ADB[@]}" get-state >/dev/null

SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
MANUFACTURER="$("${ADB[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
ABI="$("${ADB[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
RAM_KB="$("${ADB[@]}" shell cat /proc/meminfo | tr -d '\r' | awk '/MemTotal/ {print $2; exit}')"
IS_EMULATOR="$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
AIRPLANE_MODE="$("${ADB[@]}" shell settings get global airplane_mode_on | tr -d '\r')"
BATTERY_LEVEL="$("${ADB[@]}" shell dumpsys battery | awk -F': ' '/level:/ {print $2; exit}' | tr -d '\r')"

if [[ "$REQUIRE_PHYSICAL" == "1" && "$IS_EMULATOR" == "1" ]]; then
  echo "REQUIRE_PHYSICAL=1 but the selected Android device is an emulator" >&2
  exit 1
fi

cat > "$OUT_DIR/device.txt" <<EOF
library_size=$LIBRARY_SIZE
package=$PACKAGE
manufacturer=$MANUFACTURER
model=$MODEL
sdk=$SDK
abi=$ABI
ram_kb=$RAM_KB
is_emulator=$IS_EMULATOR
airplane_mode=$AIRPLANE_MODE
battery_level=$BATTERY_LEVEL
stress_iterations=$STRESS_ITERATIONS
EOF

if (( SDK < 29 )); then
  echo "Phase-3 deterministic scale seeding requires API 29+; device SDK=$SDK" >&2
  exit 1
fi

# Keep rendering measurements deterministic and avoid animation-scale noise.
"${ADB[@]}" shell settings put global window_animation_scale 0
"${ADB[@]}" shell settings put global transition_animation_scale 0
"${ADB[@]}" shell settings put global animator_duration_scale 0
"${ADB[@]}" logcat -c || true

# Resolve the exact generated connected-test task rather than hard-coding an AGP
# variant name. PhotoBook currently produces a benchmarkBenchmark test variant.
echo "[phase3] resolving connected Macrobenchmark task"
TASK_LIST="$(./gradlew :baselineprofile:tasks --all --console=plain)"
mapfile -t CONNECTED_TASKS < <(
  printf '%s\n' "$TASK_LIST" \
    | sed -n 's/^\(connected[^[:space:]]*AndroidTest\)[[:space:]].*/\1/p' \
    | grep -i 'benchmark' \
    | sort -u
)

if (( ${#CONNECTED_TASKS[@]} != 1 )); then
  echo "Expected exactly one benchmark connected AndroidTest task; found ${#CONNECTED_TASKS[@]}" >&2
  printf '%s\n' "${CONNECTED_TASKS[@]:-<none>}" >&2
  printf '%s\n' "$TASK_LIST" > "$OUT_DIR/baselineprofile-tasks.txt"
  exit 1
fi

CONNECTED_TASK="${CONNECTED_TASKS[0]}"
echo "[phase3] running :baselineprofile:$CONNECTED_TASK at librarySize=$LIBRARY_SIZE"

./gradlew ":baselineprofile:$CONNECTED_TASK" \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  -Pandroid.testInstrumentationRunnerArguments.photobook.librarySize="$LIBRARY_SIZE" \
  --stacktrace \
  | tee "$OUT_DIR/macrobenchmark-gradle.log"

# Preserve benchmark JSON / Perfetto result locations for later forensic review.
find baselineprofile/build \
  -type f \
  \( -name '*benchmarkData.json' -o -name '*.perfetto-trace' -o -name '*.json' \) \
  -print \
  | sort > "$OUT_DIR/benchmark-result-files.txt" || true

if ! "${ADB[@]}" shell pm path "$PACKAGE" 2>/dev/null | grep -q '^package:'; then
  echo "Target benchmark package is not installed after Macrobenchmark execution" >&2
  exit 1
fi

"${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$OUT_DIR/meminfo-before-stress.txt" || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/gfxinfo-before-stress.txt" || true
"${ADB[@]}" shell dumpsys cpuinfo > "$OUT_DIR/cpuinfo-before-stress.txt" || true

# Reuse the existing lifecycle/process-death/permission/low-memory harness against
# the exact benchmark-installed target package and the seeded scale corpus.
PACKAGE="$PACKAGE" \
ITERATIONS="$STRESS_ITERATIONS" \
OUT_DIR="$OUT_DIR/lifecycle-stress" \
  bash tools/benchmark/phase0_device_stress.sh

"${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$OUT_DIR/meminfo-after-stress.txt" || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/gfxinfo-after-stress.txt" || true
"${ADB[@]}" shell dumpsys battery > "$OUT_DIR/battery-after.txt" || true
"${ADB[@]}" shell dumpsys cpuinfo > "$OUT_DIR/cpuinfo-after-stress.txt" || true
"${ADB[@]}" logcat -d -b crash > "$OUT_DIR/crash-buffer.txt" || true
"${ADB[@]}" logcat -d > "$OUT_DIR/logcat.txt" || true
if (( SDK >= 30 )); then
  "${ADB[@]}" shell dumpsys activity exit-info "$PACKAGE" > "$OUT_DIR/exit-info.txt" || true
fi

if grep -Eiq 'FATAL EXCEPTION|ANR in com\.photobook\.app|OutOfMemoryError' \
  "$OUT_DIR/crash-buffer.txt" "$OUT_DIR/logcat.txt"; then
  echo "FAIL: crash/ANR/OOM evidence found in Phase-3 device run" >&2
  exit 1
fi

echo "[phase3] PASS device certification harness"
echo "[phase3] library_size=$LIBRARY_SIZE model=$MODEL sdk=$SDK ram_kb=$RAM_KB emulator=$IS_EMULATOR"
echo "[phase3] reports=$OUT_DIR"
