#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd "$SCRIPT_DIR"

if [[ ! -x "./gradlew" ]]; then
  echo "Gradle wrapper not found at repository root: $SCRIPT_DIR" >&2
  exit 1
fi

echo "Building PhotoBook from $SCRIPT_DIR"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Refusing release build from a dirty tracked worktree." >&2
  exit 1
fi
source_commit="$(git rev-parse HEAD)"

metadata="$(./gradlew -q :app:printReleaseMetadata)"
version_code="$(printf '%s\n' "$metadata" | awk -F= '$1 == "versionCode" { print $2 }')"
version_name="$(printf '%s\n' "$metadata" | awk -F= '$1 == "versionName" { print $2 }')"
if [[ -z "$version_code" || -z "$version_name" ]]; then
  echo "Unable to derive release metadata from Gradle." >&2
  exit 1
fi

./gradlew clean \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:verifyApkSize \
  :app:verifyReleaseBundleSize \
  :app:lintRelease

aab="app/build/outputs/bundle/release/app-release.aab"
if [[ ! -s "$aab" ]]; then
  echo "Release AAB was not produced: $aab" >&2
  exit 1
fi

# The Play upload artifact must be structurally valid and signed. An unsigned
# CI bundle must never be copied out as a production artifact by this script.
unzip -tqq "$aab"
if ! jarsigner -verify -verbose -certs "$aab" >/dev/null 2>&1; then
  echo "Release AAB is unsigned or its JAR signature is invalid: $aab" >&2
  exit 1
fi

aab_bytes="$(wc -c < "$aab" | tr -d '[:space:]')"
max_aab_bytes=$((20 * 1024 * 1024))
if (( aab_bytes > max_aab_bytes )); then
  echo "Final signed AAB exceeds 20 MiB: $aab_bytes bytes" >&2
  exit 1
fi

if ! unzip -l "$aab" | grep -Fq "base/assets/photobook/food_live_label_model.tflite"; then
  echo "Bundled local semantic-label model is missing from final AAB." >&2
  exit 1
fi

cert_report="$(LC_ALL=C keytool -printcert -jarfile "$aab")"
signer_sha256="$(printf '%s\n' "$cert_report" | awk -F'SHA256:' '/SHA256:/ {print $2; exit}' | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]')"
if [[ -z "$signer_sha256" ]]; then
  echo "Unable to read signer SHA-256 certificate fingerprint from final AAB." >&2
  exit 1
fi

if [[ -n "${EXPECTED_UPLOAD_CERT_SHA256:-}" ]]; then
  expected_signer="$(printf '%s' "$EXPECTED_UPLOAD_CERT_SHA256" | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]')"
  if [[ "$signer_sha256" != "$expected_signer" ]]; then
    echo "Final AAB signer does not match EXPECTED_UPLOAD_CERT_SHA256." >&2
    exit 1
  fi
fi

apks=()
while IFS= read -r apk; do
  apks+=("$apk")
done < <(find app/build/outputs/apk/release -type f -name '*.apk' -print | sort)
if [[ "${#apks[@]}" -eq 0 ]]; then
  echo "No release APKs were produced." >&2
  exit 1
fi

merged_manifest="$(find app/build/intermediates/merged_manifests/release -type f -name AndroidManifest.xml -print -quit 2>/dev/null || true)"
if [[ -z "$merged_manifest" || ! -s "$merged_manifest" ]]; then
  echo "Merged release manifest was not found." >&2
  exit 1
fi
if rg -n 'android\.permission\.INTERNET|android:name="INTERNET"' "$merged_manifest"; then
  echo "Release manifest unexpectedly contains INTERNET permission: $merged_manifest" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  [[ -s "$apk" ]] || { echo "Empty release APK: $apk" >&2; exit 1; }
done

output_dir="outputs/release"
mkdir -p "$output_dir"
release_stem="PhotoBook-v${version_name}-vc${version_code}-production"
cp "$aab" "$output_dir/${release_stem}.aab"
for apk in "${apks[@]}"; do
  cp "$apk" "$output_dir/${release_stem}-$(basename "$apk")"
done

echo "Release artifacts verified and copied to $output_dir"
echo "AAB: $output_dir/${release_stem}.aab"
echo "APK count: ${#apks[@]}"
echo "Next steps are intentionally manual: inspect/signature-verify the copied artifacts, test the exact AAB/APKs on physical devices in airplane mode, then upload through Play Console."
