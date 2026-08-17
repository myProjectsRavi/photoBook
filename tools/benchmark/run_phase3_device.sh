#!/usr/bin/env bash
set -euo pipefail

# Phase-3 scale/device certification runner.
#
# Repository production source remains unchanged except for the explicitly reviewed
# Phase-3 production fix. For benchmark execution this script may prepare an ephemeral
# release-like target in the local CI worktree: debug signing, profileable tracing,
# the repo's pinned ProfileInstaller, and (on x86_64 emulators only) an x86_64 APK
# split. Every worktree/device mutation made here is restored on exit.
# Use REQUIRE_PHYSICAL=1 for release-grade physical-device evidence.

LIBRARY_SIZE="${LIBRARY_SIZE:-10000}"
STRESS_ITERATIONS="${STRESS_ITERATIONS:-12}"
REQUIRE_PHYSICAL="${REQUIRE_PHYSICAL:-0}"
OUT_DIR="${OUT_DIR:-build/reports/phase3/device-${LIBRARY_SIZE}}"
PACKAGE="com.photobook.app"
ROOM_TEST_CLASS="com.photobook.app.verification.IndexPersistenceInstrumentedTest"

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
if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required" >&2
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

RUN_TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/photobook-phase3.XXXXXX")"
BUILD_GRADLE="app/build.gradle.kts"
BUILD_GRADLE_BACKUP="$RUN_TEMP_DIR/build.gradle.kts"
RELEASE_MANIFEST="app/src/release/AndroidManifest.xml"
RELEASE_MANIFEST_CREATED=0
BATTERY_SIMULATED=0
DEBUG_KEYSTORE_CREATED=0
DEBUG_KEYSTORE=""
ORIGINAL_WINDOW_ANIMATION="__unset__"
ORIGINAL_TRANSITION_ANIMATION="__unset__"
ORIGINAL_ANIMATOR_DURATION="__unset__"

cp "$BUILD_GRADLE" "$BUILD_GRADLE_BACKUP"

restore_global_setting() {
  local key="$1"
  local value="$2"
  if [[ "$value" == "__unset__" ]]; then
    return
  fi
  if [[ -z "$value" || "$value" == "null" ]]; then
    "${ADB[@]}" shell settings delete global "$key" >/dev/null 2>&1 || true
  else
    "${ADB[@]}" shell settings put global "$key" "$value" >/dev/null 2>&1 || true
  fi
}

cleanup_phase3() {
  set +e
  if [[ -f "$BUILD_GRADLE_BACKUP" ]]; then
    cp "$BUILD_GRADLE_BACKUP" "$BUILD_GRADLE"
  fi
  if [[ "$RELEASE_MANIFEST_CREATED" == "1" ]]; then
    rm -f "$RELEASE_MANIFEST"
  fi
  restore_global_setting window_animation_scale "$ORIGINAL_WINDOW_ANIMATION"
  restore_global_setting transition_animation_scale "$ORIGINAL_TRANSITION_ANIMATION"
  restore_global_setting animator_duration_scale "$ORIGINAL_ANIMATOR_DURATION"
  if [[ "$BATTERY_SIMULATED" == "1" ]]; then
    "${ADB[@]}" shell dumpsys battery reset >/dev/null 2>&1 || true
  fi
  if [[ "$DEBUG_KEYSTORE_CREATED" == "1" && -n "$DEBUG_KEYSTORE" ]]; then
    rm -f "$DEBUG_KEYSTORE"
  fi
  rm -rf "$RUN_TEMP_DIR"
}

trap 'status=$?; trap - EXIT INT TERM; cleanup_phase3; exit "$status"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -e "$RELEASE_MANIFEST" ]]; then
  echo "Refusing to overwrite existing $RELEASE_MANIFEST" >&2
  exit 1
fi

ORIGINAL_WINDOW_ANIMATION="$("${ADB[@]}" shell settings get global window_animation_scale | tr -d '\r')"
ORIGINAL_TRANSITION_ANIMATION="$("${ADB[@]}" shell settings get global transition_animation_scale | tr -d '\r')"
ORIGINAL_ANIMATOR_DURATION="$("${ADB[@]}" shell settings get global animator_duration_scale | tr -d '\r')"

"${ADB[@]}" shell dumpsys battery > "$OUT_DIR/battery-before.txt"
BATTERY_BEFORE="$(cat "$OUT_DIR/battery-before.txt" | tr -d '\r')"
if [[ "$IS_EMULATOR" == "1" ]]; then
  BATTERY_SIMULATED=1
  "${ADB[@]}" shell dumpsys battery unplug >/dev/null
  "${ADB[@]}" shell dumpsys battery set status 3 >/dev/null
else
  if printf '%s\n' "$BATTERY_BEFORE" \
    | grep -Eiq '(AC powered|USB powered|Wireless powered|Dock powered): true'; then
    echo "Physical Phase-3 device must be unplugged from all charging sources" >&2
    exit 1
  fi
fi
"${ADB[@]}" shell dumpsys battery > "$OUT_DIR/battery-benchmark-state.txt"
if grep -Eiq '(AC powered|USB powered|Wireless powered|Dock powered): true' \
  "$OUT_DIR/battery-benchmark-state.txt"; then
  echo "Phase-3 core benchmark requires a non-charging device state" >&2
  exit 1
fi
BATTERY_BENCHMARK_STATUS="$(
  awk -F': ' '/^[[:space:]]*status:/ {print $2; exit}' "$OUT_DIR/battery-benchmark-state.txt" \
    | tr -d '\r'
)"
if [[ "$IS_EMULATOR" == "1" ]]; then
  if [[ "$BATTERY_BENCHMARK_STATUS" != "3" ]]; then
    echo "Phase-3 emulator must report BatteryManager status 3 (DISCHARGING); got $BATTERY_BENCHMARK_STATUS" >&2
    exit 1
  fi
else
  case "$BATTERY_BENCHMARK_STATUS" in
    3|4) ;;
    *)
      echo "Physical Phase-3 device must report battery status 3 (DISCHARGING) or 4 (NOT_CHARGING); got $BATTERY_BENCHMARK_STATUS" >&2
      exit 1
      ;;
  esac
fi
printf 'battery_status_benchmark=%s\n' "$BATTERY_BENCHMARK_STATUS" >> "$OUT_DIR/device.txt"

# Keep rendering measurements deterministic and avoid animation-scale noise.
"${ADB[@]}" shell settings put global window_animation_scale 0
"${ADB[@]}" shell settings put global transition_animation_scale 0
"${ADB[@]}" shell settings put global animator_duration_scale 0
"${ADB[@]}" logcat -c || true

# Macrobenchmark needs a release-like, non-debuggable, profileable target that is
# installable without production signing material. Prepare that only inside this
# ephemeral worktree; cleanup_phase3 restores it on every exit path.
mkdir -p "$(dirname "$RELEASE_MANIFEST")"
cat > "$RELEASE_MANIFEST" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <profileable
            android:shell="true"
            tools:targetApi="29" />
    </application>
</manifest>
EOF
RELEASE_MANIFEST_CREATED=1
cp "$RELEASE_MANIFEST" "$OUT_DIR/phase3-release-manifest-overlay.xml"

# Production APKs intentionally ship ARM splits only. GitHub's KVM-backed Android
# emulator is x86_64, so add x86_64 to this worktree only when that is the selected
# device ABI. Also expose the repo's already-pinned ProfileInstaller 1.4.1 to the
# ephemeral release target. Fail closed if either expected line moves.
PHASE3_X86="$([[ "$IS_EMULATOR" == "1" && "$ABI" == "x86_64" ]] && echo 1 || echo 0)"
PHASE3_X86="$PHASE3_X86" python3 - <<'PY'
import os
from pathlib import Path

path = Path("app/build.gradle.kts")
text = path.read_text()

if os.environ.get("PHASE3_X86") == "1":
    old_abi = 'include("arm64-v8a", "armeabi-v7a")'
    new_abi = 'include("arm64-v8a", "armeabi-v7a", "x86_64")'
    if text.count(old_abi) != 1:
        raise SystemExit(
            "Expected exactly one production ABI split declaration before Phase-3 CI patch"
        )
    text = text.replace(old_abi, new_abi)

profile_line = '    add("benchmarkImplementation", "androidx.profileinstaller:profileinstaller:1.4.1")'
if text.count(profile_line) != 1:
    raise SystemExit(
        "Expected exactly one pinned benchmark ProfileInstaller dependency before Phase-3 CI patch"
    )
text = text.replace(
    profile_line,
    profile_line + '\n' +
    '    add("releaseImplementation", "androidx.profileinstaller:profileinstaller:1.4.1")',
)
path.write_text(text)
PY

git diff -- app/build.gradle.kts > "$OUT_DIR/phase3-ci-build.patch" || true

# AGP supports an injected signing override. Use an isolated throwaway debug key
# so no release key or GitHub Secret is required or exposed. A caller-supplied
# keystore must already exist and is never deleted by this runner.
if [[ -n "${PHASE3_DEBUG_KEYSTORE:-}" ]]; then
  DEBUG_KEYSTORE="$PHASE3_DEBUG_KEYSTORE"
  if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
    echo "PHASE3_DEBUG_KEYSTORE does not exist: $DEBUG_KEYSTORE" >&2
    exit 1
  fi
else
  DEBUG_KEYSTORE="$RUN_TEMP_DIR/photobook-phase3-debug.keystore"
  keytool -genkeypair \
    -keystore "$DEBUG_KEYSTORE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=PhotoBook Phase3,C=US" \
    -noprompt >/dev/null 2>&1
  DEBUG_KEYSTORE_CREATED=1
fi
DEBUG_KEYSTORE="$(cd "$(dirname "$DEBUG_KEYSTORE")" && pwd)/$(basename "$DEBUG_KEYSTORE")"

# The baselineprofile module currently exposes several connected variants. This
# is the release-like Macrobenchmark variant already proven to compile the
# minified release target; Phase-3 makes that target installable above.
echo "[phase3] resolving connected Macrobenchmark task"
TASK_LIST="$(./gradlew :baselineprofile:tasks --all --console=plain)"
CONNECTED_TASK="connectedBenchmarkBenchmarkAndroidTest"

if ! printf '%s\n' "$TASK_LIST" | grep -Eq "^${CONNECTED_TASK}[[:space:]]"; then
  echo "Expected :baselineprofile:$CONNECTED_TASK but it was not generated" >&2
  printf '%s\n' "$TASK_LIST" \
    | sed -n 's/^\(connected[^[:space:]]*AndroidTest\)[[:space:]].*/\1/p' \
    | sort -u \
    | tee "$OUT_DIR/available-connected-android-tests.txt" >&2
  printf '%s\n' "$TASK_LIST" > "$OUT_DIR/baselineprofile-tasks.txt"
  exit 1
fi

# Exercise the real Room database once, at the smallest matrix size, before the
# scale corpus is seeded. This crosses both 200-row write and stale-delete
# boundaries without adding a second emulator job or contaminating MediaStore.
if [[ "$LIBRARY_SIZE" == "10000" ]]; then
  echo "[phase3] running focused Room persistence regression"
  set +e
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$ROOM_TEST_CLASS" \
    --stacktrace \
    | tee "$OUT_DIR/room-regression-gradle.log"
  ROOM_TEST_STATUS=${PIPESTATUS[0]}
  set -e

  mkdir -p "$OUT_DIR/room-regression"
  cp -R app/build/outputs/androidTest-results "$OUT_DIR/room-regression/" 2>/dev/null || true
  cp -R app/build/reports/androidTests "$OUT_DIR/room-regression/" 2>/dev/null || true

  # connectedDebugAndroidTest can leave the debug target installed. Remove it so
  # the release-like Macrobenchmark install cannot collide with a different signer.
  "${ADB[@]}" uninstall "${PACKAGE}.test" >/dev/null 2>&1 || true
  "${ADB[@]}" uninstall "$PACKAGE" >/dev/null 2>&1 || true

  if (( ROOM_TEST_STATUS != 0 )); then
    echo "Focused Room persistence regression failed" >&2
    exit "$ROOM_TEST_STATUS"
  fi
fi

echo "[phase3] running :baselineprofile:$CONNECTED_TASK at librarySize=$LIBRARY_SIZE"

./gradlew ":baselineprofile:$CONNECTED_TASK" \
  -Pandroid.injected.signing.store.file="$DEBUG_KEYSTORE" \
  -Pandroid.injected.signing.store.password=android \
  -Pandroid.injected.signing.key.alias=androiddebugkey \
  -Pandroid.injected.signing.key.password=android \
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
