#!/usr/bin/env python3
"""Fail-closed validation for PhotoBook Macrobenchmark frame metrics."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any

REQUIRED_INTERACTIONS = (
    "e_gridScrollFrameTiming",
    "f_searchTypingAndResultsFrameTiming",
    "g_reelsVerticalSwipeFrameTiming",
)
DEFAULT_CPU_P95_MS = 16.67
DEFAULT_CPU_P99_MS = 33.34
DEFAULT_OVERRUN_P95_MS = 0.0
DEFAULT_OVERRUN_P99_MS = 16.67
MIN_FRAME_SAMPLES = 20


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--benchmark-dir", required=True, type=Path)
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--max-cpu-p95-ms", type=float, default=DEFAULT_CPU_P95_MS)
    parser.add_argument("--max-cpu-p99-ms", type=float, default=DEFAULT_CPU_P99_MS)
    parser.add_argument("--max-overrun-p95-ms", type=float, default=DEFAULT_OVERRUN_P95_MS)
    parser.add_argument("--max-overrun-p99-ms", type=float, default=DEFAULT_OVERRUN_P99_MS)
    parser.add_argument(
        "--observe-only",
        action="store_true",
        help=(
            "Compute and retain frame-budget violations without treating hosted-emulator "
            "timings as physical-device smoothness certification."
        ),
    )
    return parser.parse_args()


def nearest_rank(values: list[float], percentile: int) -> float:
    if not values:
        raise ValueError("values must not be empty")
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile * len(ordered) / 100.0))
    return ordered[min(rank - 1, len(ordered) - 1)]


def flatten_runs(metric: Any, metric_name: str, benchmark_name: str) -> list[float]:
    if not isinstance(metric, dict):
        raise ValueError(f"{benchmark_name}: {metric_name} is not an object")
    runs = metric.get("runs")
    if not isinstance(runs, list):
        raise ValueError(f"{benchmark_name}: {metric_name}.runs is missing")

    values: list[float] = []
    for iteration in runs:
        if not isinstance(iteration, list):
            raise ValueError(
                f"{benchmark_name}: {metric_name}.runs must be a list of per-iteration lists"
            )
        for value in iteration:
            if not isinstance(value, (int, float)) or not math.isfinite(float(value)):
                raise ValueError(f"{benchmark_name}: {metric_name} contains a non-finite sample")
            values.append(float(value))
    if len(values) < MIN_FRAME_SAMPLES:
        raise ValueError(
            f"{benchmark_name}: {metric_name} has only {len(values)} samples; "
            f"need at least {MIN_FRAME_SAMPLES}"
        )
    return values


def load_most_complete_json(root: Path) -> tuple[Path, dict[str, Any]]:
    candidates: list[tuple[int, int, Path, dict[str, Any]]] = []
    for path in sorted(root.glob("**/*benchmarkData.json")):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        benchmarks = payload.get("benchmarks") if isinstance(payload, dict) else None
        if isinstance(benchmarks, list):
            required_present = sum(
                isinstance(item, dict) and item.get("name") in REQUIRED_INTERACTIONS
                for item in benchmarks
            )
            candidates.append((required_present, len(benchmarks), path, payload))
    if not candidates:
        raise ValueError(f"No valid benchmarkData.json found under {root}")
    _, _, path, payload = max(candidates, key=lambda item: (item[0], item[1], item[2].stat().st_mtime_ns))
    return path, payload


def validate(
    benchmark_dir: Path,
    api_level: int,
    cpu_p95_limit: float = DEFAULT_CPU_P95_MS,
    cpu_p99_limit: float = DEFAULT_CPU_P99_MS,
    overrun_p95_limit: float = DEFAULT_OVERRUN_P95_MS,
    overrun_p99_limit: float = DEFAULT_OVERRUN_P99_MS,
    enforce_budgets: bool = True,
) -> dict[str, Any]:
    source, payload = load_most_complete_json(benchmark_dir)
    benchmarks = {
        item.get("name"): item
        for item in payload["benchmarks"]
        if isinstance(item, dict) and isinstance(item.get("name"), str)
    }

    missing = [name for name in REQUIRED_INTERACTIONS if name not in benchmarks]
    if missing:
        raise ValueError(
            "benchmarkData.json is incomplete; missing interaction benchmark(s): "
            + ", ".join(missing)
        )

    summary: dict[str, Any] = {
        "source": str(source),
        "apiLevel": api_level,
        "thresholds": {
            "frameDurationCpuMsP95Max": cpu_p95_limit,
            "frameDurationCpuMsP99Max": cpu_p99_limit,
            "frameOverrunMsP95Max": overrun_p95_limit if api_level >= 31 else None,
            "frameOverrunMsP99Max": overrun_p99_limit if api_level >= 31 else None,
        },
        "benchmarks": {},
        "budgetEnforced": enforce_budgets,
        "certificationStatus": "ENFORCED" if enforce_budgets else "OBSERVATIONAL_ONLY",
        "passed": True,
        "withinBudget": False,
        "failures": [],
        "budgetViolations": [],
    }

    failures: list[str] = []
    for name in REQUIRED_INTERACTIONS:
        sampled = benchmarks[name].get("sampledMetrics")
        if not isinstance(sampled, dict):
            raise ValueError(f"{name}: sampledMetrics is missing")

        cpu = flatten_runs(sampled.get("frameDurationCpuMs"), "frameDurationCpuMs", name)
        cpu_p95 = nearest_rank(cpu, 95)
        cpu_p99 = nearest_rank(cpu, 99)
        result: dict[str, Any] = {
            "frameDurationCpuMs": {
                "sampleCount": len(cpu),
                "p95": cpu_p95,
                "p99": cpu_p99,
            }
        }
        if cpu_p95 > cpu_p95_limit:
            failures.append(f"{name}: frameDurationCpuMs P95 {cpu_p95:.3f} > {cpu_p95_limit:.3f}")
        if cpu_p99 > cpu_p99_limit:
            failures.append(f"{name}: frameDurationCpuMs P99 {cpu_p99:.3f} > {cpu_p99_limit:.3f}")

        if api_level >= 31:
            overrun = flatten_runs(sampled.get("frameOverrunMs"), "frameOverrunMs", name)
            overrun_p95 = nearest_rank(overrun, 95)
            overrun_p99 = nearest_rank(overrun, 99)
            result["frameOverrunMs"] = {
                "sampleCount": len(overrun),
                "p95": overrun_p95,
                "p99": overrun_p99,
                "jankSampleCount": sum(value > 0.0 for value in overrun),
                "jankSamplePercent": 100.0 * sum(value > 0.0 for value in overrun) / len(overrun),
            }
            if overrun_p95 > overrun_p95_limit:
                failures.append(
                    f"{name}: frameOverrunMs P95 {overrun_p95:.3f} > {overrun_p95_limit:.3f}"
                )
            if overrun_p99 > overrun_p99_limit:
                failures.append(
                    f"{name}: frameOverrunMs P99 {overrun_p99:.3f} > {overrun_p99_limit:.3f}"
                )

        summary["benchmarks"][name] = result

    summary["budgetViolations"] = failures
    # Execution success and measured frame-budget compliance are independent.
    # Hosted emulators may complete successfully without satisfying physical-device budgets.
    summary["withinBudget"] = not failures
    if enforce_budgets:
        summary["failures"] = failures
        summary["passed"] = not failures
    else:
        summary["failures"] = []
        summary["passed"] = True
    return summary


def main() -> None:
    args = parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        summary = validate(
            benchmark_dir=args.benchmark_dir,
            api_level=args.api_level,
            cpu_p95_limit=args.max_cpu_p95_ms,
            cpu_p99_limit=args.max_cpu_p99_ms,
            overrun_p95_limit=args.max_overrun_p95_ms,
            overrun_p99_limit=args.max_overrun_p99_ms,
            enforce_budgets=not args.observe_only,
        )
    except ValueError as exc:
        summary = {
            "passed": False,
            "failures": [str(exc)],
            "apiLevel": args.api_level,
        }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(args.output.read_text(encoding="utf-8"), end="")
    if not summary.get("passed"):
        raise SystemExit("Frame smoothness certification failed")


if __name__ == "__main__":
    main()
