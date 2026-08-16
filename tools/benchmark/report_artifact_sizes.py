#!/usr/bin/env python3
"""Report APK/AAB size composition for Phase-0 regression tracking."""

from __future__ import annotations

import argparse
import json
import zipfile
from collections import defaultdict
from pathlib import Path

MODEL_SUFFIXES = (".tflite", ".lite", ".model")


def category_for(name: str) -> str:
    parts = name.split("/")
    leaf = parts[-1]
    if leaf.startswith("classes") and leaf.endswith(".dex"):
        return "dex"
    if leaf.lower().endswith(MODEL_SUFFIXES):
        return "models"
    if name.startswith("base/") and len(parts) > 1:
        second = parts[1]
        if second in {"dex", "lib", "assets", "res", "root", "manifest"}:
            return second
    if parts[0] in {"lib", "assets", "res", "META-INF"}:
        return parts[0].lower()
    if leaf == "resources.arsc":
        return "resources"
    return "other"


def inspect_artifact(path: Path) -> dict:
    categories: dict[str, int] = defaultdict(int)
    uncompressed_categories: dict[str, int] = defaultdict(int)
    largest: list[tuple[int, int, str]] = []

    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            category = category_for(info.filename)
            categories[category] += info.compress_size
            uncompressed_categories[category] += info.file_size
            largest.append((info.compress_size, info.file_size, info.filename))

    largest.sort(reverse=True)
    return {
        "path": str(path),
        "artifact_bytes": path.stat().st_size,
        "compressed_categories_bytes": dict(sorted(categories.items())),
        "uncompressed_categories_bytes": dict(sorted(uncompressed_categories.items())),
        "largest_entries": [
            {
                "name": name,
                "compressed_bytes": compressed,
                "uncompressed_bytes": uncompressed,
            }
            for compressed, uncompressed, name in largest[:25]
        ],
    }


def load_baseline(path: Path | None) -> dict[str, int]:
    if path is None or not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return {
        Path(item["path"]).name: int(item["artifact_bytes"])
        for item in data.get("artifacts", [])
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("app/build/outputs"))
    parser.add_argument("--output", type=Path, default=Path("build/reports/phase0/artifact-sizes.json"))
    parser.add_argument("--baseline-report", type=Path)
    args = parser.parse_args()

    artifacts = sorted(
        path
        for path in args.root.rglob("*")
        if path.is_file() and path.suffix.lower() in {".apk", ".aab"}
    )
    if not artifacts:
        raise SystemExit(f"No APK/AAB artifacts found under {args.root}")

    baseline = load_baseline(args.baseline_report)
    inspected = []
    for path in artifacts:
        item = inspect_artifact(path)
        previous = baseline.get(path.name)
        item["baseline_bytes"] = previous
        item["delta_bytes"] = None if previous is None else item["artifact_bytes"] - previous
        item["delta_percent"] = (
            None
            if previous in (None, 0)
            else round((item["artifact_bytes"] - previous) * 100.0 / previous, 4)
        )
        inspected.append(item)

    report = {
        "root": str(args.root),
        "artifact_count": len(inspected),
        "artifacts": inspected,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    for item in inspected:
        delta = item["delta_bytes"]
        delta_text = "baseline unavailable" if delta is None else f"delta={delta:+d} bytes"
        print(f"{Path(item['path']).name}: {item['artifact_bytes']} bytes ({delta_text})")
    print(f"size report: {args.output}")


if __name__ == "__main__":
    main()
