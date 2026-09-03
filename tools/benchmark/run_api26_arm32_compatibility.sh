#!/usr/bin/env bash
set -euo pipefail

# Release-grade Android 8/8.1 compatibility gate for PhotoBook's 32-bit APK.
#
# This wrapper intentionally does NOT use an emulator or Macrobenchmark. It:
#   - builds a release-like/minified benchmark APK from one explicitly pinned app-source SHA;
#   - refuses emulator targets, non-API26/27 devices, and devices without armeabi-v7a support;
#   - refuses a device with any visible image rows, avoiding accidental processing of personal media;
#   - requires airplane mode and verifies the packaged/installed app has no INTERNET permission;
#   - installs only the armeabi-v7a benchmark split and verifies Android selected that primary ABI;
#   - delegates lifecycle/process-death/trim-memory/permission stress to the pinned source tree;
#   - requires at least 50 stress iterations and removes the test app on exit.
#
# Use only on a dedicated test device. Example for the current 2.0.15 clean candidate:
#   APP_SOURCE_SHA=04909b6e485c1ede12160aeac136598bee8d303f \
#   ITERATIONS=50 \
#   bash tools/benchmark/run_api26_arm32_compatibility.sh

PACKAGE="${PACKAGE:-com.photobook.app}"
ACTIVITY="${ACTIVITY:-com.photobook.app/.MainActivity}"
APP_SOURCE_SHA="${APP_SOURCE_SHA:-}"
ITERATIONS="${ITERATIONS:-50}"
OUT_DIR="${OUT_DIR:-build/reports/phase5/api26-arm32-compatibility}"

if [[ -z "$APP_SOURCE_SHA" ]]; then
  echo "APP_SOURCE_SHA is required; refusing an unpinned physical-device certification run" >&2
  exit 2
fi
if [[ ! "$ITERATIONS" =~ ^[0-9]+$ ]] || (( 10#$ITERATIONS < 50 )); then
  echo "API26/27 physical compatibility requires ITERATIONS to be an integer >= 50; got: $ITERATIONS" >&2
  exit 2
fi
for command_name in adb git unzip sha256sum; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required" >&2
    exit 2
  fi
done

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
if ! git cat-file -e "${APP_SOURCE_SHA}^{commit}" 2>/dev/null; then
  echo "APP_SOURCE_SHA is not available in this repository: $APP_SOURCE_SHA" >&2
  exit 2
fi
RESOLVED_SHA="$(git rev-parse "${APP_SOURCE_SHA}^{commit}")"
if [[ "$RESOLVED_SHA" != "$APP_SOURCE_SHA" ]]; then
  echo "APP_SOURCE_SHA must be a full immutable commit SHA; resolved $APP_SOURCE_SHA -> $RESOLVED_SHA" >&2
  exit 2
fi

case "$OUT_DIR" in
  /*) ;;
  *) OUT_DIR="$REPO_ROOT/$OUT_DIR" ;;
esac
mkdir -p "$OUT_DIR"

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=( -s "$ANDROID_SERIAL" )
fi
"${ADB[@]}" get-state >/dev/null

SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
MANUFACTURER="$("${ADB[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
ABI="$("${ADB[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
ABILIST="$("${ADB[@]}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
ABILIST32="$("${ADB[@]}" shell getprop ro.product.cpu.abilist32 | tr -d '\r')"
QEMU_KERNEL="$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
QEMU_BOOT="$("${ADB[@]}" shell getprop ro.boot.qemu | tr -d '\r')"
AIRPLANE_MODE="$("${ADB[@]}" shell settings get global airplane_mode_on | tr -d '\r')"
RAM_KB="$("${ADB[@]}" shell cat /proc/meminfo | tr -d '\r' | awk '/MemTotal/ {print $2; exit}')"
BATTERY_LEVEL="$("${ADB[@]}" shell dumpsys battery | awk -F': ' '/level:/ {print $2; exit}' | tr -d '\r')"

if [[ "$QEMU_KERNEL" == "1" || "$QEMU_BOOT" == "1" ]]; then
  echo "API26/27 ARM32 certification requires a physical device; emulator detected" >&2
  exit 1
fi
case "$SDK" in
  26|27) ;;
  *)
    echo "API26/27 ARM32 certification requires Android API 26 or 27; device SDK=$SDK" >&2
    exit 1
    ;;
esac
if ! printf '%s\n%s\n' "$ABILIST" "$ABILIST32" | tr ',' '\n' | grep -Fxq 'armeabi-v7a'; then
  echo "Device does not advertise armeabi-v7a support; abilist=$ABILIST abilist32=$ABILIST32" >&2
  exit 1
fi
if [[ "$AIRPLANE_MODE" != "1" ]]; then
  echo "API26/27 ARM32 certification requires airplane mode enabled before the run" >&2
  exit 1
fi

# Compatibility replay can cause PhotoBook to inspect the visible MediaStore.
# Refuse any existing image rows rather than touch or inspect personal media.
MEDIA_ROWS="$("${ADB[@]}" shell content query \
  --uri content://media/external/images/media \
  --projection _id 2>&1 || true)"
if grep -Eiq 'Error while accessing provider|Exception|SecurityException' <<<"$MEDIA_ROWS"; then
  echo "Unable to verify that the physical device has an empty visible image library" >&2
  exit 1
fi
if grep -Eq '^Row:' <<<"$MEDIA_ROWS"; then
  echo "Dedicated API26/27 compatibility device must have zero visible image rows; refusing this device" >&2
  exit 1
fi

if "${ADB[@]}" shell pm path "$PACKAGE" 2>/dev/null | grep -q '^package:'; then
  echo "$PACKAGE is already installed; refusing to overwrite or delete existing app data on the selected device" >&2
  exit 1
fi

cat > "$OUT_DIR/device-preflight.txt" <<EOF
app_source_sha=$APP_SOURCE_SHA
package=$PACKAGE
activity=$ACTIVITY
manufacturer=$MANUFACTURER
model=$MODEL
sdk=$SDK
primary_device_abi=$ABI
abilist=$ABILIST
abilist32=$ABILIST32
ro_kernel_qemu=${QEMU_KERNEL:-<empty>}
ro_boot_qemu=${QEMU_BOOT:-<empty>}
airplane_mode=$AIRPLANE_MODE
ram_kb=$RAM_KB
battery_level=$BATTERY_LEVEL
iterations=$ITERATIONS
EOF

RUN_TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/photobook-api26-arm32.XXXXXX")"
APP_WORKTREE="$RUN_TEMP_DIR/app-source"
INSTALLED_BY_SCRIPT=0

cleanup() {
  status=$?
  trap - EXIT INT TERM
  set +e
  if (( INSTALLED_BY_SCRIPT == 1 )); then
    "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
    "${ADB[@]}" uninstall "$PACKAGE" > "$OUT_DIR/uninstall.txt" 2>&1 || true
  fi
  if [[ -d "$APP_WORKTREE" ]]; then
    git -C "$REPO_ROOT" worktree remove --force "$APP_WORKTREE" >/dev/null 2>&1 || true
  fi
  rm -rf "$RUN_TEMP_DIR"
  exit "$status"
}
trap cleanup EXIT INT TERM

git worktree add --detach "$APP_WORKTREE" "$APP_SOURCE_SHA" >/dev/null
if [[ "$(git -C "$APP_WORKTREE" rev-parse HEAD)" != "$APP_SOURCE_SHA" ]]; then
  echo "Temporary app worktree did not resolve to the pinned candidate SHA" >&2
  exit 1
fi
if [[ -n "$(git -C "$APP_WORKTREE" status --porcelain)" ]]; then
  echo "Pinned app worktree is unexpectedly dirty" >&2
  exit 1
fi

(
  cd "$APP_WORKTREE"
  chmod +x ./gradlew
  ./gradlew :app:assembleBenchmark
)

mapfile -t ARM32_APKS < <(find "$APP_WORKTREE/app/build/outputs/apk/benchmark" \
  -type f -name '*armeabi-v7a*.apk' -print | sort)
if (( ${#ARM32_APKS[@]} != 1 )); then
  echo "Expected exactly one armeabi-v7a benchmark APK; found ${#ARM32_APKS[@]}" >&2
  printf '%s\n' "${ARM32_APKS[@]}" >&2
  exit 1
fi
APK="${ARM32_APKS[0]}"

mapfile -t PACKAGED_ABIS < <(unzip -Z1 "$APK" \
  | awk -F/ '/^lib\/[^/]+\/[^/]+\.so$/ {print $2}' \
  | sort -u)
if (( ${#PACKAGED_ABIS[@]} != 1 )) || [[ "${PACKAGED_ABIS[0]}" != "armeabi-v7a" ]]; then
  echo "ARM32 benchmark APK contains unexpected native ABIs: ${PACKAGED_ABIS[*]:-<none>}" >&2
  exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
AAPT="$(command -v aapt 2>/dev/null || true)"
if [[ -z "$AAPT" && -n "$SDK_ROOT" ]]; then
  AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt 2>/dev/null | sort | tail -n 1)"
fi
if [[ -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo "aapt is required to prove the APK requests no INTERNET permission" >&2
  exit 2
fi
if "$AAPT" dump permissions "$APK" | grep -q 'android.permission.INTERNET'; then
  echo "FAIL: packaged ARM32 benchmark APK requests android.permission.INTERNET" >&2
  exit 1
fi

APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
APK_BYTES="$(wc -c < "$APK" | tr -d ' ')"
cat > "$OUT_DIR/apk.txt" <<EOF
app_source_sha=$APP_SOURCE_SHA
apk_sha256=$APK_SHA256
apk_bytes=$APK_BYTES
packaged_abis=${PACKAGED_ABIS[*]}
packaged_internet_permission=ABSENT
EOF

"${ADB[@]}" logcat -c || true
"${ADB[@]}" install "$APK" > "$OUT_DIR/install.txt"
INSTALLED_BY_SCRIPT=1

PACKAGE_DUMP="$("${ADB[@]}" shell dumpsys package "$PACKAGE")"
printf '%s\n' "$PACKAGE_DUMP" > "$OUT_DIR/package-dump.txt"
if grep -q 'android.permission.INTERNET' <<<"$PACKAGE_DUMP"; then
  echo "FAIL: installed package metadata contains android.permission.INTERNET" >&2
  exit 1
fi
PRIMARY_CPU_ABI="$(sed -n 's/^[[:space:]]*primaryCpuAbi=//p' <<<"$PACKAGE_DUMP" | head -n 1 | tr -d '\r')"
if [[ "$PRIMARY_CPU_ABI" != "armeabi-v7a" ]]; then
  echo "Installed target did not select the 32-bit ABI; primaryCpuAbi=$PRIMARY_CPU_ABI" >&2
  exit 1
fi

START_OUTPUT="$("${ADB[@]}" shell am start -W -n "$ACTIVITY" 2>&1)"
printf '%s\n' "$START_OUTPUT" > "$OUT_DIR/initial-launch.txt"
if ! grep -Eq '^Status:[[:space:]]*ok$' <<<"$START_OUTPUT"; then
  echo "Initial API26/27 ARM32 launch did not report Status: ok" >&2
  cat "$OUT_DIR/initial-launch.txt" >&2
  exit 1
fi

PACKAGE="$PACKAGE" \
ACTIVITY="$ACTIVITY" \
ITERATIONS="$ITERATIONS" \
OUT_DIR="$OUT_DIR/lifecycle-stress" \
PRESERVE_LOGCAT=1 \
ANDROID_SERIAL="${ANDROID_SERIAL:-}" \
  bash "$APP_WORKTREE/tools/benchmark/phase0_device_stress.sh"

"${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$OUT_DIR/meminfo-final.txt" || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/gfxinfo-final.txt" || true
"${ADB[@]}" logcat -d -b crash > "$OUT_DIR/crash-buffer-final.txt" || true
"${ADB[@]}" logcat -d > "$OUT_DIR/logcat-final.txt" || true

if grep -Eiq 'FATAL EXCEPTION|ANR in com\.photobook\.app|OutOfMemoryError' \
  "$OUT_DIR/crash-buffer-final.txt" "$OUT_DIR/logcat-final.txt"; then
  echo "FAIL: crash/ANR/OOM evidence found after API26/27 ARM32 compatibility replay" >&2
  exit 1
fi

cat > "$OUT_DIR/result.txt" <<EOF
api26_arm32_physical_compatibility=PASS
app_source_sha=$APP_SOURCE_SHA
sdk=$SDK
model=$MODEL
primary_cpu_abi=$PRIMARY_CPU_ABI
iterations=$ITERATIONS
airplane_mode=$AIRPLANE_MODE
packaged_internet_permission=ABSENT
installed_internet_permission=ABSENT
apk_sha256=$APK_SHA256
EOF

cat "$OUT_DIR/result.txt"
echo "reports: $OUT_DIR"
