#!/usr/bin/env bash
set -euo pipefail

# Physical Phase-5 certification must never silently fall back to the generic
# emulator/non-physical path because of a misspelled REQUIRE_PHYSICAL value.
if [[ -n "${REQUIRE_PHYSICAL+x}" && "$REQUIRE_PHYSICAL" != "1" ]]; then
  echo "Phase-5 physical certification requires REQUIRE_PHYSICAL=1; refusing override: $REQUIRE_PHYSICAL" >&2
  exit 2
fi

# The Phase-5 acceptance gate requires the full lifecycle stress pass. Never let
# an inherited/typo value silently reduce the configured 12-iteration minimum.
if [[ -n "${STRESS_ITERATIONS+x}" ]]; then
  if [[ ! "$STRESS_ITERATIONS" =~ ^[0-9]+$ ]] || (( 10#$STRESS_ITERATIONS < 12 )); then
    echo "Phase-5 physical certification requires STRESS_ITERATIONS to be an integer >= 12; refusing: $STRESS_ITERATIONS" >&2
    exit 2
  fi
else
  STRESS_ITERATIONS=12
fi

export REQUIRE_PHYSICAL=1
export STRESS_ITERATIONS
exec bash tools/benchmark/run_phase3_device.sh "$@"
