#!/usr/bin/env python3
"""Extract and validate Phase 4 index/startup timing markers from device evidence."""

from __future__ import annotations

import argparse
import json
import re
import statistics
from pathlib import Path

PHASE4_TAG = "PhotoBookPhase4"
INDEX_READY_RE = re.compile(
    r"\[phase3\]\s+indexReady\s+librarySize=(?P<size>\d+)\s+"
    r"elapsedMs=(?P<elapsed>\d+)\s+photosPerSecond=(?P<rate>[0-9.]+)"
)
KV_RE = re.compile(r"(?P<key>[A-Za-z][A-Za-z0-9_]*)=(?P<value>[^\s]+)")
REQUIRED_STAGES = ("media_store_scan", "record_build", "room_fts_persist")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--logcat", required=True, type=Path)
    parser.add_argument("--macro-log", required=True, type=Path)
    parser.add_argument("--library-size", required=True, type=int)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--raw-output", required=True, type=Path)
    return parser.parse_args()


def parse_marker(line: str) -> dict[str, str] | None:
    if PHASE4_TAG not in line or "stage=" not in line:
        return None
    marker = {match.group("key"): match.group("value") for match in KV_RE.finditer(line)}
    return marker if "stage" in marker else None


def as_int(marker: dict[str, str], key: str) -> int:
    try:
        return int(marker[key])
    except (KeyError, ValueError) as exc:
        raise SystemExit(f"Invalid or missing {key!r} in marker: {marker}") from exc


def percentile_nearest_rank(values: list[int], percentile: int) -> int:
    if not values:
        raise ValueError("values must not be empty")
    ordered = sorted(values)
    rank = max(1, (percentile * len(ordered) + 99) // 100)
    return ordered[min(rank - 1, len(ordered) - 1)]


def main() -> None:
    args = parse_args()
    if args.library_size not in {10_000, 50_000, 100_000}:
        raise SystemExit("library size must be 10000, 50000, or 100000")
    if not args.logcat.is_file():
        raise SystemExit(f"Missing logcat: {args.logcat}")
    if not args.macro_log.is_file():
        raise SystemExit(f"Missing Macrobenchmark log: {args.macro_log}")

    raw_lines: list[str] = []
    markers: list[dict[str, str]] = []
    for line in args.logcat.read_text(errors="replace").splitlines():
        marker = parse_marker(line)
        if marker is not None:
            raw_lines.append(line)
            markers.append(marker)

    args.raw_output.parent.mkdir(parents=True, exist_ok=True)
    args.raw_output.write_text("\n".join(raw_lines) + ("\n" if raw_lines else ""))

    stage_markers: dict[str, list[dict[str, str]]] = {}
    for marker in markers:
        stage_markers.setdefault(marker["stage"], []).append(marker)

    selected: dict[str, dict[str, str]] = {}
    for stage in REQUIRED_STAGES:
        matches = [
            marker
            for marker in stage_markers.get(stage, [])
            if as_int(marker, "count") == args.library_size
        ]
        if not matches:
            raise SystemExit(
                f"Missing {stage} marker with count={args.library_size}; "
                f"available={stage_markers.get(stage, [])}"
            )
        selected[stage] = matches[0]

    record_build = selected["record_build"]
    record_build_elapsed = as_int(record_build, "elapsedMs")
    exif_elapsed = as_int(record_build, "exifElapsedMs")
    geocode_elapsed = as_int(record_build, "geocodeElapsedMs")
    geocode_count = as_int(record_build, "geocodeCount")
    if exif_elapsed > record_build_elapsed:
        raise SystemExit("EXIF cumulative time exceeds total record-build wall time")
    if geocode_elapsed > record_build_elapsed:
        raise SystemExit("Geocode cumulative time exceeds total record-build wall time")
    if geocode_count < 0 or geocode_count > args.library_size:
        raise SystemExit("Invalid geocodeCount")

    macro_text = args.macro_log.read_text(errors="replace")
    index_ready_matches = [
        match for match in INDEX_READY_RE.finditer(macro_text)
        if int(match.group("size")) == args.library_size
    ]
    if not index_ready_matches:
        raise SystemExit(
            f"Missing [phase3] indexReady measurement for librarySize={args.library_size}"
        )
    total_index_ready_ms = int(index_ready_matches[0].group("elapsed"))

    media_store_scan_ms = as_int(selected["media_store_scan"], "elapsedMs")
    room_fts_persist_ms = as_int(selected["room_fts_persist"], "elapsedMs")
    attributed_ms = media_store_scan_ms + record_build_elapsed + room_fts_persist_ms
    residual_ms = total_index_ready_ms - attributed_ms
    if residual_ms < 0:
        raise SystemExit(
            "Attributed stage wall time exceeds total index-ready latency: "
            f"total={total_index_ready_ms} attributed={attributed_ms}"
        )

    warm_load_samples = [
        as_int(marker, "elapsedMs")
        for marker in stage_markers.get("persisted_load", [])
        if as_int(marker, "count") == args.library_size
    ]
    initial_empty_load_samples = [
        as_int(marker, "elapsedMs")
        for marker in stage_markers.get("persisted_load", [])
        if as_int(marker, "count") == 0
    ]
    if not warm_load_samples:
        raise SystemExit(
            f"Missing persisted_load marker with count={args.library_size}; warm-start attribution unavailable"
        )
    if not initial_empty_load_samples:
        raise SystemExit("Missing initial persisted_load marker with count=0")

    summary = {
        "librarySize": args.library_size,
        "totalIndexReadyMs": total_index_ready_ms,
        "mediaStoreScanMs": media_store_scan_ms,
        "recordBuildMs": record_build_elapsed,
        "exifCumulativeMs": exif_elapsed,
        "geocodeCumulativeMs": geocode_elapsed,
        "geocodeCount": geocode_count,
        "roomFtsPersistMs": room_fts_persist_ms,
        "residualPublicationReadinessMs": residual_ms,
        "attributedMs": attributed_ms,
        "attributedPercent": round(attributed_ms * 100.0 / total_index_ready_ms, 2),
        "warmPersistedLoad": {
            "samples": len(warm_load_samples),
            "minMs": min(warm_load_samples),
            "p50Ms": percentile_nearest_rank(warm_load_samples, 50),
            "p95Ms": percentile_nearest_rank(warm_load_samples, 95),
            "maxMs": max(warm_load_samples),
            "meanMs": round(statistics.fmean(warm_load_samples), 2),
        },
        "initialEmptyPersistedLoadMs": initial_empty_load_samples[0],
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
