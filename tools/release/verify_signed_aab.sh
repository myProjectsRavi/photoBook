#!/usr/bin/env bash
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/release/app-release.aab}"
EXPECTED_UPLOAD_CERT_SHA256="${EXPECTED_UPLOAD_CERT_SHA256:-}"
REPORT_DIR="${REPORT_DIR:-build/reports/release-signing}"

if [[ -z "$EXPECTED_UPLOAD_CERT_SHA256" ]]; then
  echo "EXPECTED_UPLOAD_CERT_SHA256 is required" >&2
  exit 2
fi
for cmd in jarsigner keytool zipinfo sha256sum; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "$cmd is required" >&2; exit 2; }
done
[[ -f "$AAB" ]] || { echo "AAB not found: $AAB" >&2; exit 2; }

export LC_ALL=C
mkdir -p "$REPORT_DIR"

normalize_fingerprint() {
  tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

expected="$(printf '%s' "$EXPECTED_UPLOAD_CERT_SHA256" | normalize_fingerprint)"
if [[ ! "$expected" =~ ^[0-9A-F]{64}$ ]]; then
  echo "EXPECTED_UPLOAD_CERT_SHA256 must be a 32-byte SHA-256 fingerprint" >&2
  exit 2
fi

if ! zipinfo -1 "$AAB" | grep -Eq '^META-INF/[^/]+\.(RSA|DSA|EC)$'; then
  echo "FAIL: AAB contains no JAR signature block" >&2
  exit 1
fi

jarsigner -verify -verbose -certs "$AAB" > "$REPORT_DIR/jarsigner.txt" 2>&1 || {
  cat "$REPORT_DIR/jarsigner.txt" >&2
  echo "FAIL: jarsigner verification failed" >&2
  exit 1
}
cat "$REPORT_DIR/jarsigner.txt"
if ! grep -qi 'jar verified' "$REPORT_DIR/jarsigner.txt"; then
  echo "FAIL: jarsigner did not report 'jar verified'" >&2
  exit 1
fi
if grep -qi 'jar is unsigned' "$REPORT_DIR/jarsigner.txt"; then
  echo "FAIL: jarsigner reports the AAB is unsigned" >&2
  exit 1
fi

keytool -printcert -jarfile "$AAB" > "$REPORT_DIR/certificate.txt" 2>&1 || {
  cat "$REPORT_DIR/certificate.txt" >&2
  echo "FAIL: unable to extract signer certificate from AAB" >&2
  exit 1
}
cat "$REPORT_DIR/certificate.txt"
actual_raw="$(sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' "$REPORT_DIR/certificate.txt" | head -n 1)"
actual="$(printf '%s' "$actual_raw" | normalize_fingerprint)"
if [[ ! "$actual" =~ ^[0-9A-F]{64}$ ]]; then
  echo "FAIL: could not parse signer SHA-256 fingerprint" >&2
  exit 1
fi
if [[ "$actual" != "$expected" ]]; then
  echo "FAIL: signer fingerprint mismatch" >&2
  echo "expected_sha256=$expected" >&2
  echo "actual_sha256=$actual" >&2
  exit 1
fi

aab_sha256="$(sha256sum "$AAB" | awk '{print $1}')"
aab_bytes="$(wc -c < "$AAB" | tr -d ' ')"
cat > "$REPORT_DIR/result.txt" <<EOF
signed_aab_verification=PASS
aab=$AAB
aab_sha256=$aab_sha256
aab_bytes=$aab_bytes
signer_sha256=$actual
EOF
cat "$REPORT_DIR/result.txt"
