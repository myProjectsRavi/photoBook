#!/usr/bin/env python3
"""Fail-closed structural verification for the accepted PhotoBook Blueprint 01 handoff."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

STORY_IDS = [f"S{index:02d}" for index in range(1, 33)]


def parse_backlog(text: str) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in text.splitlines():
        if not re.match(r"^\| S\d\d \|", line):
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) < 7:
            raise ValueError(f"malformed backlog row: {line}")
        story_id = parts[1]
        status = parts[5]
        if story_id in rows:
            raise ValueError(f"duplicate story row: {story_id}")
        rows[story_id] = status
    return rows


def validate(backlog_text: str, state_text: str) -> list[str]:
    errors: list[str] = []
    try:
        rows = parse_backlog(backlog_text)
    except ValueError as error:
        return [str(error)]

    if list(rows) != STORY_IDS:
        errors.append("backlog must contain exactly S01-S32 in fixed order")
    for story_id in STORY_IDS[:-1]:
        if rows.get(story_id) != "ACCEPTED":
            errors.append(f"{story_id} must be ACCEPTED before S32 final verification")
    if rows.get("S32") not in {"IN_PROGRESS", "ACCEPTED"}:
        errors.append("S32 must be IN_PROGRESS or ACCEPTED")

    required_state = {
        "authorized_branch": "autopilot/epics-features-user-stories",
        "current_story": "S32 Final verification",
    }
    for key, expected in required_state.items():
        match = re.search(rf"^{re.escape(key)}:\s*(.+?)\s*$", state_text, re.MULTILINE)
        actual = match.group(1).strip() if match else None
        if actual != expected:
            errors.append(f"{key} mismatch: expected {expected!r}, got {actual!r}")

    status_match = re.search(r"^status:\s*(\S+)\s*$", state_text, re.MULTILINE)
    if not status_match or status_match.group(1) not in {"IN_PROGRESS", "ACCEPTED"}:
        errors.append("STATE status must be IN_PROGRESS or ACCEPTED during/following S32")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backlog", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    args = parser.parse_args()

    errors = validate(
        args.backlog.read_text(encoding="utf-8"),
        args.state.read_text(encoding="utf-8"),
    )
    if errors:
        for error in errors:
            print(f"blueprint completion error: {error}")
        return 1
    print("blueprint completion structure valid: S01-S31 accepted; S32 final verification active/accepted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
