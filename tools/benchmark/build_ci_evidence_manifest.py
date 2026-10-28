#!/usr/bin/env python3
"""Build and validate a fail-closed GitHub Actions evidence manifest for PhotoBook."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
OK_RE = re.compile(r"OK \((\d+) tests?\)")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _suite_counts(root: ET.Element) -> tuple[int, int, int, int]:
    if root.tag == "testsuite":
        return (
            int(root.attrib.get("tests", "0")),
            int(root.attrib.get("failures", "0")),
            int(root.attrib.get("errors", "0")),
            int(root.attrib.get("skipped", "0")),
        )
    if root.tag == "testsuites" and "tests" in root.attrib:
        return (
            int(root.attrib.get("tests", "0")),
            int(root.attrib.get("failures", "0")),
            int(root.attrib.get("errors", "0")),
            int(root.attrib.get("skipped", "0")),
        )
    counts = [0, 0, 0, 0]
    for child in root.findall("./testsuite"):
        child_counts = _suite_counts(child)
        for index, value in enumerate(child_counts):
            counts[index] += value
    return counts[0], counts[1], counts[2], counts[3]


def collect_evidence(
    *,
    checkout_sha: str,
    workflow_revision: str,
    workflow_ref: str,
    api: str,
    abi: str,
    avd_ram_mb: int,
    fixture: Path,
    test_results_root: Path | None,
    instrumentation_output: Path | None,
    apk_root: Path,
) -> tuple[dict[str, object], list[str]]:
    errors: list[str] = []

    if not SHA_RE.fullmatch(checkout_sha):
        errors.append("checkout SHA must be an exact 40-character lowercase Git SHA")
    if not workflow_revision.strip():
        errors.append("workflow revision is required")
    if not workflow_ref.strip():
        errors.append("workflow ref is required")
    if not api.strip():
        errors.append("Android API is required")
    if not abi.strip():
        errors.append("Android ABI is required")
    if avd_ram_mb <= 0:
        errors.append("AVD RAM must be positive")

    fixture_record: dict[str, object] | None = None
    if fixture.is_file():
        fixture_record = {"path": fixture.as_posix(), "sha256": sha256_file(fixture)}
    else:
        errors.append(f"required fixture is missing: {fixture}")

    apk_records: list[dict[str, object]] = []
    if apk_root.is_dir():
        for apk in sorted(apk_root.rglob("*.apk")):
            if apk.is_file():
                apk_records.append(
                    {
                        "path": apk.as_posix(),
                        "bytes": apk.stat().st_size,
                        "sha256": sha256_file(apk),
                    }
                )
    if not apk_records:
        errors.append(f"no APK evidence found under: {apk_root}")

    tests = failures = test_errors = skipped = 0
    parsed_xml: list[str] = []
    instrumentation_record: dict[str, object] | None = None

    if instrumentation_output is not None:
        if instrumentation_output.is_file():
            output_text = instrumentation_output.read_text(encoding="utf-8", errors="replace")
            matches = OK_RE.findall(output_text)
            if matches:
                tests = int(matches[-1])
            failure_markers = [
                marker
                for marker in ("FAILURES", "INSTRUMENTATION_FAILED", "Process crashed")
                if marker in output_text
            ]
            if failure_markers:
                failures = 1
                errors.append(
                    "instrumentation output contains failure marker(s): "
                    + ", ".join(failure_markers)
                )
            instrumentation_record = {
                "path": instrumentation_output.as_posix(),
                "sha256": sha256_file(instrumentation_output),
            }
        else:
            errors.append(f"required instrumentation output is missing: {instrumentation_output}")
    elif test_results_root is not None:
        xml_files = (
            sorted(test_results_root.rglob("TEST-*.xml"))
            if test_results_root.is_dir()
            else []
        )
        for xml_path in xml_files:
            try:
                root = ET.parse(xml_path).getroot()
                suite_tests, suite_failures, suite_errors, suite_skipped = _suite_counts(root)
            except (ET.ParseError, OSError, ValueError) as exc:
                errors.append(f"cannot parse test result {xml_path}: {exc}")
                continue
            tests += suite_tests
            failures += suite_failures
            test_errors += suite_errors
            skipped += suite_skipped
            parsed_xml.append(xml_path.as_posix())
        if not xml_files:
            errors.append(f"no instrumentation TEST-*.xml files found under: {test_results_root}")
    else:
        errors.append("one instrumentation evidence source is required")

    if tests <= 0:
        errors.append("required instrumentation suite collected zero test cases")
    if failures > 0 or test_errors > 0:
        errors.append(
            f"instrumentation suite is not green: failures={failures} errors={test_errors}"
        )

    manifest: dict[str, object] = {
        "schema_version": 1,
        "status": "VALID" if not errors else "INVALID",
        "checkout_sha": checkout_sha,
        "workflow_revision": workflow_revision,
        "workflow_ref": workflow_ref,
        "emulator": {
            "api": api,
            "abi": abi,
            "avd_ram_mb": avd_ram_mb,
            "network_required_by_app": False,
        },
        "fixture": fixture_record,
        "apks": apk_records,
        "tests": {
            "count": tests,
            "failures": failures,
            "errors": test_errors,
            "skipped": skipped,
            "xml_files": parsed_xml,
            "instrumentation_output": instrumentation_record,
        },
        "validation_errors": errors,
    }
    return manifest, errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkout-sha", required=True)
    parser.add_argument("--workflow-revision", required=True)
    parser.add_argument("--workflow-ref", required=True)
    parser.add_argument("--api", required=True)
    parser.add_argument("--abi", required=True)
    parser.add_argument("--avd-ram-mb", required=True, type=int)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--test-results-root", type=Path)
    parser.add_argument("--instrumentation-output", type=Path)
    parser.add_argument("--apk-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest, errors = collect_evidence(
        checkout_sha=args.checkout_sha,
        workflow_revision=args.workflow_revision,
        workflow_ref=args.workflow_ref,
        api=args.api,
        abi=args.abi,
        avd_ram_mb=args.avd_ram_mb,
        fixture=args.fixture,
        test_results_root=args.test_results_root,
        instrumentation_output=args.instrumentation_output,
        apk_root=args.apk_root,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        for error in errors:
            print(f"evidence error: {error}", file=sys.stderr)
        return 1
    test_info = manifest["tests"]
    print(
        "evidence manifest valid: "
        f"tests={test_info['count']} apks={len(manifest['apks'])} "
        f"checkout={manifest['checkout_sha']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
