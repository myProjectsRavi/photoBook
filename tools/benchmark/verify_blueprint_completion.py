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
    branch_match = re.search(r"^authorized_branch:\s*(.+?)\s*$", state_text, re.MULTILINE)
    branch = branch_match.group(1).strip() if branch_match else None
    if branch != "autopilot/epics-features-user-stories":
        errors.append(
            "authorized_branch mismatch: expected "
            "'autopilot/epics-features-user-stories', got "
            f"{branch!r}"
        )

    remediation_match = re.search(r"^review_remediation:\s*(\S+)\s*$", state_text, re.MULTILINE)
    remediation = remediation_match is not None and remediation_match.group(1).lower() == "true"
    current_match = re.search(r"^current_story:\s*(S\d\d)\b.*$", state_text, re.MULTILINE)
    current_story_id = current_match.group(1) if current_match else None
    status_match = re.search(r"^status:\s*(\S+)\s*$", state_text, re.MULTILINE)
    state_status = status_match.group(1) if status_match else None

    if remediation:
        incomplete = [story_id for story_id in STORY_IDS if rows.get(story_id) != "ACCEPTED"]
        if not incomplete:
            errors.append("review remediation mode requires at least one reopened story")
        else:
            expected_current = incomplete[0]
            if current_story_id != expected_current:
                errors.append(
                    f"current_story mismatch in remediation: expected {expected_current!r}, "
                    f"got {current_story_id!r}"
                )
            if rows.get(expected_current) not in {"IN_PROGRESS", "BLOCKED"}:
                errors.append(f"{expected_current} must be IN_PROGRESS or BLOCKED in remediation")
        invalid = {
            story_id: status
            for story_id, status in rows.items()
            if status not in {"ACCEPTED", "IN_PROGRESS", "BLOCKED"}
        }
        if invalid:
            errors.append(f"invalid remediation statuses: {invalid}")
        if state_status not in {"IN_PROGRESS", "BLOCKED"}:
            errors.append("STATE status must be IN_PROGRESS or BLOCKED during remediation")
        return errors

    for story_id in STORY_IDS[:-1]:
        if rows.get(story_id) != "ACCEPTED":
            errors.append(f"{story_id} must be ACCEPTED before S32 final verification")
    if rows.get("S32") not in {"IN_PROGRESS", "ACCEPTED"}:
        errors.append("S32 must be IN_PROGRESS or ACCEPTED")

    if current_story_id != "S32":
        errors.append(
            f"current_story mismatch: expected 'S32 Final verification', got {current_story_id!r}"
        )
    if state_status not in {"IN_PROGRESS", "ACCEPTED"}:
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
    print("blueprint durable structure valid for final verification or explicit review remediation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
