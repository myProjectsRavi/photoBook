#!/usr/bin/env bash
set -euo pipefail

# Physical Phase-5 certification must never silently fall back to the generic
# emulator/non-physical path because of a misspelled REQUIRE_PHYSICAL value.
if [[ -n "${REQUIRE_PHYSICAL+x}" && "$REQUIRE_PHYSICAL" != "1" ]]; then
  echo "Phase-5 physical certification requires REQUIRE_PHYSICAL=1; refusing override: $REQUIRE_PHYSICAL" >&2
  exit 2
fi

export REQUIRE_PHYSICAL=1
exec bash tools/benchmark/run_phase3_device.sh "$@"
