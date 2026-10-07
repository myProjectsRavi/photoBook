#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-full}"
if [[ "$MODE" != "full" && "$MODE" != "--host-only" ]]; then
  echo "usage: $0 [--host-only]" >&2
  exit 2
fi

REPORT_ROOT="${PHASE0_REPORT_ROOT:-build/reports/phase0}"
FIXTURE_ROOT="${PHASE0_FIXTURE_ROOT:-build/phase0-fixtures}"
mkdir -p "$REPORT_ROOT"

command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required" >&2
  exit 2
}

printf '%s\n' "[phase0] compiling host Python tooling"
python3 -m py_compile \
  tools/benchmark/generate_media_fixtures.py \
  tools/benchmark/report_artifact_sizes.py \
  tools/benchmark/build_ci_evidence_manifest.py \
  tools/benchmark/build_compatibility_report.py \
  tools/benchmark/verify_blueprint_completion.py \
  tools/benchmark/test_phase0_tools.py \
  tools/benchmark/test_ci_evidence_manifest.py \
  tools/benchmark/test_compatibility_report.py \
  tools/benchmark/test_blueprint_completion.py

printf '%s\n' "[phase0] running host self-tests"
python3 -m unittest \
  tools/benchmark/test_phase0_tools.py \
  tools/benchmark/test_ci_evidence_manifest.py \
  tools/benchmark/test_compatibility_report.py \
  tools/benchmark/test_blueprint_completion.py

printf '%s\n' "[phase0] generating deterministic 303-record smoke corpus"
rm -rf "$FIXTURE_ROOT"
python3 tools/benchmark/generate_media_fixtures.py \
  --count 303 \
  --output "$FIXTURE_ROOT" \
  --write-media

test -f "$FIXTURE_ROOT/manifest-303.jsonl"
test -f "$FIXTURE_ROOT/summary-303.json"

if [[ "$MODE" == "--host-only" ]]; then
  printf '%s\n' "[phase0] PASS host-only verification"
  exit 0
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
  exit 2
fi

command -v java >/dev/null 2>&1 || {
  echo "Java is required" >&2
  exit 2
}

test -f ./gradlew || {
  echo "gradlew not found at repository root" >&2
  exit 2
}
chmod +x ./gradlew

printf '%s\n' "[phase0] running Android Gradle verification"
./gradlew \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:assembleBenchmark \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:verifyApkSize \
  :app:verifyReleaseBundleSize \
  :baselineprofile:assembleBenchmarkBenchmark

AAPT="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt -print | sort -V | tail -n 1)"
if [[ -z "$AAPT" ]]; then
  echo "aapt not found under $SDK_ROOT/build-tools" >&2
  exit 2
fi

mapfile -t release_apks < <(find app/build/outputs/apk/release -type f -name '*.apk' -print | sort)
if [[ "${#release_apks[@]}" -eq 0 ]]; then
  echo "No release APKs were generated" >&2
  exit 1
fi

printf '%s\n' "[phase0] verifying release APKs request no Internet permission"
PERMISSION_REPORT="$REPORT_ROOT/release-permissions.txt"
: > "$PERMISSION_REPORT"
for apk in "${release_apks[@]}"; do
  if "$AAPT" dump permissions "$apk" | grep -Fq 'android.permission.INTERNET'; then
    echo "FAIL: $apk requests android.permission.INTERNET" >&2
    exit 1
  fi
  echo "PASS: $apk has no android.permission.INTERNET"
  printf '%s no_internet=PASS\n' "$(basename "$apk")" >> "$PERMISSION_REPORT"
done

printf '%s\n' "[phase0] recording release dependency provenance"
./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
  > "$REPORT_ROOT/release-runtime-dependencies.txt"

printf '%s\n' "[phase0] reporting APK/AAB size composition"
python3 tools/benchmark/report_artifact_sizes.py \
  --root app/build/outputs \
  --output "$REPORT_ROOT/artifact-sizes.json"

printf '%s\n' "[phase0] building fail-closed compatibility/provenance report"
python3 tools/benchmark/build_compatibility_report.py \
  --gradle-file app/build.gradle.kts \
  --dependency-report "$REPORT_ROOT/release-runtime-dependencies.txt" \
  --permission-report "$PERMISSION_REPORT" \
  --apk-root app/build/outputs/apk/release \
  --bundle-root app/build/outputs/bundle/release \
  --output "$REPORT_ROOT/compatibility-report.json"

test -f app/schemas/com.photobook.app.data.db.PhotoBookDatabase/12.json || {
  echo "Room schema v12 export is missing" >&2
  exit 1
}

printf '%s\n' "[phase0] PASS full local verification"
printf '%s\n' "[phase0] reports: $REPORT_ROOT"
