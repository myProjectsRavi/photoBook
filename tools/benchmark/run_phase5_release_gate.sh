#!/usr/bin/env bash
set -euo pipefail

EXPECTED_CANDIDATE_SHA="9fd29f9987f6879f330b5b9cb4f33cde3c9b6d23"
APP_SOURCE_SHA="${APP_SOURCE_SHA:-$EXPECTED_CANDIDATE_SHA}"
MODE="${MODE:-}"

if [[ "$APP_SOURCE_SHA" != "$EXPECTED_CANDIDATE_SHA" ]]; then
  echo "Phase-5 release gate is pinned to $EXPECTED_CANDIDATE_SHA; refusing APP_SOURCE_SHA=$APP_SOURCE_SHA" >&2
  exit 2
fi
case "$MODE" in
  api26-arm32|api29-scale) ;;
  *)
    echo "MODE must be one of: api26-arm32, api29-scale" >&2
    exit 2
    ;;
esac

for command_name in git bash; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required" >&2
    exit 2
  fi
done

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
if ! git cat-file -e "${APP_SOURCE_SHA}^{commit}" 2>/dev/null; then
  echo "Pinned candidate commit is not available locally: $APP_SOURCE_SHA" >&2
  exit 2
fi
if [[ "$(git rev-parse "${APP_SOURCE_SHA}^{commit}")" != "$APP_SOURCE_SHA" ]]; then
  echo "Pinned candidate must resolve to the exact immutable SHA" >&2
  exit 2
fi

REPORT_ROOT="${REPORT_ROOT:-$REPO_ROOT/build/reports/phase5/release-gate}"
mkdir -p "$REPORT_ROOT"

case "$MODE" in
  api26-arm32)
    ITERATIONS="${ITERATIONS:-50}"
    if [[ ! "$ITERATIONS" =~ ^[0-9]+$ ]] || (( 10#$ITERATIONS < 50 )); then
      echo "api26-arm32 mode requires ITERATIONS >= 50" >&2
      exit 2
    fi
    APP_SOURCE_SHA="$APP_SOURCE_SHA" \
    ITERATIONS="$ITERATIONS" \
    OUT_DIR="$REPORT_ROOT/api26-arm32" \
      bash tools/benchmark/run_api26_arm32_compatibility.sh
    ;;

  api29-scale)
    LIBRARY_SIZE="${LIBRARY_SIZE:-10000}"
    STRESS_ITERATIONS="${STRESS_ITERATIONS:-12}"
    case "$LIBRARY_SIZE" in
      10000|50000|100000) ;;
      *)
        echo "api29-scale mode requires LIBRARY_SIZE=10000, 50000, or 100000" >&2
        exit 2
        ;;
    esac
    if [[ ! "$STRESS_ITERATIONS" =~ ^[0-9]+$ ]] || (( 10#$STRESS_ITERATIONS < 12 )); then
      echo "api29-scale mode requires STRESS_ITERATIONS >= 12" >&2
      exit 2
    fi

    RUN_TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/photobook-phase5-release.XXXXXX")"
    APP_WORKTREE="$RUN_TEMP_DIR/app-source"
    cleanup() {
      status=$?
      trap - EXIT INT TERM
      set +e
      if [[ -d "$APP_WORKTREE" ]]; then
        git -C "$REPO_ROOT" worktree remove --force "$APP_WORKTREE" >/dev/null 2>&1 || true
      fi
      rm -rf "$RUN_TEMP_DIR"
      exit "$status"
    }
    trap cleanup EXIT INT TERM

    git worktree add --detach "$APP_WORKTREE" "$APP_SOURCE_SHA" >/dev/null
    if [[ "$(git -C "$APP_WORKTREE" rev-parse HEAD)" != "$APP_SOURCE_SHA" ]]; then
      echo "Temporary Phase-5 worktree did not resolve to the pinned candidate" >&2
      exit 1
    fi
    if [[ -n "$(git -C "$APP_WORKTREE" status --porcelain)" ]]; then
      echo "Pinned Phase-5 app worktree is unexpectedly dirty" >&2
      exit 1
    fi

    (
      cd "$APP_WORKTREE"
      REQUIRE_PHYSICAL=1 \
      LIBRARY_SIZE="$LIBRARY_SIZE" \
      STRESS_ITERATIONS="$STRESS_ITERATIONS" \
      OUT_DIR="$REPORT_ROOT/api29-scale-${LIBRARY_SIZE}" \
        bash tools/benchmark/run_phase5_physical_device.sh
    )
    ;;
esac

cat > "$REPORT_ROOT/release-gate-summary.txt" <<EOF
phase5_release_gate=PASS
app_source_sha=$APP_SOURCE_SHA
mode=$MODE
EOF

echo "Phase-5 release gate PASS"
echo "app_source_sha=$APP_SOURCE_SHA mode=$MODE"
echo "reports=$REPORT_ROOT"
