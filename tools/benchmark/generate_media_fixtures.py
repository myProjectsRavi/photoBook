#!/usr/bin/env python3
"""Generate deterministic PhotoBook scale/performance fixtures without network access.

The generator is intentionally host-side and never ships in the Android artifact.
It produces a JSONL manifest for 10k/50k/100k scale tests and can optionally write
small valid PNG files for MediaStore/device seeding. Semantic archive cases are
recorded as explicit expectations so dangerous false positives are part of every
baseline corpus even when synthetic pixels cannot represent real-world semantics.
"""

from __future__ import annotations

import argparse
import binascii
import json
import random
import struct
import zlib
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

DEFAULT_SEED = 20260816
DEFAULT_COUNTS = (10_000, 50_000, 100_000)

SCENARIOS = (
    "camera_general",
    "screenshot_payment_positive",
    "screenshot_nonpayment_negative",
    "cooked_food_positive",
    "fmcg_packaged_food_positive",
    "livestock_negative",
    "wildlife_negative",
    "raw_ingredient_negative",
    "sensitive_document_negative",
    "favorite_protected",
    "large_photo",
    "corrupt_media",
    "zero_byte_media",
)


@dataclass(frozen=True)
class FixtureRecord:
    id: int
    relative_path: str
    file_name: str
    scenario: str
    date_added_ms: int
    width: int
    height: int
    mime_type: str
    is_favorite: bool
    expected_archive: str
    expected_archive_reason: str
    expected_search_terms: tuple[str, ...]


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    body = kind + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", binascii.crc32(body) & 0xFFFFFFFF)


def make_png(index: int, width: int = 32, height: int = 32) -> bytes:
    """Create a tiny deterministic RGB PNG using only the Python standard library."""
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(
                (
                    (index * 17 + x * 7 + y * 3) & 0xFF,
                    (index * 31 + x * 5 + y * 11) & 0xFF,
                    (index * 47 + x * 13 + y * 2) & 0xFF,
                )
            )
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(rows), level=9))
        + png_chunk(b"IEND", b"")
    )


def scenario_for(index: int) -> str:
    # Weighted toward normal camera photos while guaranteeing regular adversarial cases.
    selector = index % 101
    if selector < 58:
        return "camera_general"
    if selector < 65:
        return "screenshot_payment_positive"
    if selector < 71:
        return "screenshot_nonpayment_negative"
    if selector < 77:
        return "cooked_food_positive"
    if selector < 82:
        return "fmcg_packaged_food_positive"
    if selector < 87:
        return "livestock_negative"
    if selector < 90:
        return "wildlife_negative"
    if selector < 93:
        return "raw_ingredient_negative"
    if selector < 96:
        return "sensitive_document_negative"
    if selector < 98:
        return "favorite_protected"
    if selector == 98:
        return "large_photo"
    if selector == 99:
        return "corrupt_media"
    return "zero_byte_media"


def archive_expectation(scenario: str) -> tuple[str, str]:
    if scenario == "screenshot_payment_positive":
        return "payments_candidate", "strong payment screenshot cues"
    if scenario == "cooked_food_positive":
        return "food_candidate", "cooked/prepared/served food only"
    if scenario == "fmcg_packaged_food_positive":
        return "food_candidate", "FMCG packaged food only"
    if scenario == "livestock_negative":
        return "never_food", "livestock must never be archived as food"
    if scenario == "wildlife_negative":
        return "never_food", "live animal/wildlife must never be archived as food"
    if scenario == "raw_ingredient_negative":
        return "never_food", "raw ingredient alone is insufficient"
    if scenario == "sensitive_document_negative":
        return "never_archive", "sensitive document protection"
    if scenario == "favorite_protected":
        return "never_archive", "favorites are protected"
    if scenario in {"corrupt_media", "zero_byte_media"}:
        return "never_archive", "unreadable/invalid media must fail safely"
    return "none", "no archive expectation"


def build_record(index: int, start: datetime, rng: random.Random) -> FixtureRecord:
    scenario = scenario_for(index)
    age = timedelta(minutes=index * 7)
    timestamp = int((start - age).timestamp() * 1000)
    is_favorite = scenario == "favorite_protected" or index % 211 == 0
    width, height = (4032, 3024) if scenario == "large_photo" else (1080, 1440)

    if scenario.startswith("screenshot"):
        folder = "Pictures/Screenshots"
        prefix = "Screenshot"
        terms = ("screenshot", "payment" if scenario.endswith("positive") else "chat")
    else:
        folder = "DCIM/Camera"
        prefix = "IMG"
        terms = ("camera", scenario.replace("_", " "))

    salt = rng.randrange(1_000_000)
    file_name = f"{prefix}_{index:06d}_{salt:06d}.png"
    expected_archive, expected_reason = archive_expectation(scenario)

    return FixtureRecord(
        id=index + 1,
        relative_path=f"{folder}/{file_name}",
        file_name=file_name,
        scenario=scenario,
        date_added_ms=timestamp,
        width=width,
        height=height,
        mime_type="image/png",
        is_favorite=is_favorite,
        expected_archive=expected_archive,
        expected_archive_reason=expected_reason,
        expected_search_terms=terms,
    )


def write_fixture_media(root: Path, record: FixtureRecord) -> None:
    target = root / record.relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    if record.scenario == "zero_byte_media":
        target.write_bytes(b"")
    elif record.scenario == "corrupt_media":
        target.write_bytes(b"\x89PNG\r\n\x1a\ncorrupt-photobook-fixture")
    else:
        target.write_bytes(make_png(record.id))


def generate(count: int, output: Path, seed: int, write_media: bool) -> None:
    output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    start = datetime(2026, 8, 16, 0, 0, tzinfo=timezone.utc)
    manifest_path = output / f"manifest-{count}.jsonl"
    summary: dict[str, int] = {scenario: 0 for scenario in SCENARIOS}

    with manifest_path.open("w", encoding="utf-8") as manifest:
        for index in range(count):
            record = build_record(index, start, rng)
            summary[record.scenario] = summary.get(record.scenario, 0) + 1
            manifest.write(json.dumps(asdict(record), sort_keys=True) + "\n")
            if write_media:
                write_fixture_media(output / f"media-{count}", record)

    missing = [scenario for scenario, seen in summary.items() if seen == 0 and count >= len(SCENARIOS)]
    if missing:
        raise RuntimeError(f"fixture distribution failed to cover scenarios: {missing}")

    metadata = {
        "count": count,
        "seed": seed,
        "write_media": write_media,
        "manifest": manifest_path.name,
        "scenarios": summary,
        "archive_safety_contract": {
            "positive_food": ["cooked_food_positive", "fmcg_packaged_food_positive"],
            "must_never_be_food": [
                "livestock_negative",
                "wildlife_negative",
                "raw_ingredient_negative",
            ],
            "policy": "uncertain -> do not archive",
        },
    }
    (output / f"summary-{count}.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, action="append", help="Fixture count; repeat for multiple scales.")
    parser.add_argument("--output", type=Path, default=Path("build/phase0-fixtures"))
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument(
        "--write-media",
        action="store_true",
        help="Also create tiny PNG media. Omit for fast manifest-only 10k/50k/100k generation.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    counts = tuple(args.count) if args.count else DEFAULT_COUNTS
    for count in counts:
        if count <= 0:
            raise SystemExit("--count must be positive")
        generate(count=count, output=args.output, seed=args.seed, write_media=args.write_media)
        print(f"generated deterministic PhotoBook fixture manifest: count={count} seed={args.seed}")


if __name__ == "__main__":
    main()
