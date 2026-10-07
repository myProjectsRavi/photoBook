#!/usr/bin/env python3
"""Build fail-closed PhotoBook package/API/ABI/dependency compatibility evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

APK_MAX_BYTES = 30_000_000
AAB_MAX_BYTES = 20 * 1024 * 1024
ALLOWED_ABIS = {"arm64-v8a", "armeabi-v7a"}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _int_value(text: str, name: str) -> int | None:
    match = re.search(rf"\b{name}\s*=\s*(\d+)", text)
    return int(match.group(1)) if match else None


def _string_value(text: str, name: str) -> str | None:
    match = re.search(rf'\b{name}\s*=\s*"([^"]+)"', text)
    return match.group(1) if match else None


def _named_block(text: str, name: str, start: int = 0) -> str | None:
    match = re.search(rf"\b{re.escape(name)}\s*\{{", text[start:])
    if not match:
        return None
    open_brace = start + match.end() - 1
    depth = 0
    for index in range(open_brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace + 1:index]
    return None


def _declared_abis(text: str) -> set[str]:
    android_block = _named_block(text, "android")
    if android_block is None:
        return set()
    splits_block = _named_block(android_block, "splits")
    if splits_block is None:
        return set()
    abi_block = _named_block(splits_block, "abi")
    if abi_block is None:
        return set()
    match = re.search(r"\binclude\(([^)]*)\)", abi_block)
    if not match:
        return set()
    return set(re.findall(r'"([^"]+)"', match.group(1)))


def collect(
    *,
    gradle_file: Path,
    dependency_report: Path,
    permission_report: Path,
    apk_root: Path,
    bundle_root: Path,
) -> tuple[dict[str, object], list[str]]:
    errors: list[str] = []
    if not gradle_file.is_file():
        return {}, [f"missing Gradle configuration: {gradle_file}"]
    gradle_text = gradle_file.read_text(encoding="utf-8")

    compile_sdk = _int_value(gradle_text, "compileSdk")
    min_sdk = _int_value(gradle_text, "minSdk")
    target_sdk = _int_value(gradle_text, "targetSdk")
    application_id = _string_value(gradle_text, "applicationId")
    declared_abis = _declared_abis(gradle_text)

    if not application_id:
        errors.append("applicationId is missing")
    if None in (compile_sdk, min_sdk, target_sdk):
        errors.append("compileSdk/minSdk/targetSdk must all be declared")
    elif not (min_sdk <= target_sdk <= compile_sdk):
        errors.append("API matrix must satisfy minSdk <= targetSdk <= compileSdk")
    if declared_abis != ALLOWED_ABIS:
        errors.append(
            f"declared ABI split mismatch: expected {sorted(ALLOWED_ABIS)}, got {sorted(declared_abis)}"
        )

    if not dependency_report.is_file() or dependency_report.stat().st_size == 0:
        errors.append(f"release dependency report missing/empty: {dependency_report}")
        dependency_record = None
    else:
        dependency_record = {
            "path": dependency_report.as_posix(),
            "bytes": dependency_report.stat().st_size,
            "sha256": sha256_file(dependency_report),
        }

    permission_lines = (
        permission_report.read_text(encoding="utf-8").splitlines()
        if permission_report.is_file()
        else []
    )
    no_internet_apks = {
        line.split(" ", 1)[0]
        for line in permission_lines
        if line.endswith(" no_internet=PASS")
    }

    apk_records: list[dict[str, object]] = []
    native_abis: set[str] = set()
    apks = sorted(apk_root.rglob("*.apk")) if apk_root.is_dir() else []
    if not apks:
        errors.append(f"no release APKs found under {apk_root}")
    for apk in apks:
        size = apk.stat().st_size
        if size > APK_MAX_BYTES:
            errors.append(f"{apk.name} exceeds {APK_MAX_BYTES} bytes: {size}")
        if apk.name not in no_internet_apks:
            errors.append(f"no-INTERNET evidence missing for {apk.name}")

        native_libs: list[dict[str, object]] = []
        with zipfile.ZipFile(apk) as archive:
            for name in sorted(archive.namelist()):
                match = re.fullmatch(r"lib/([^/]+)/([^/]+\.so)", name)
                if not match:
                    continue
                abi, soname = match.groups()
                native_abis.add(abi)
                payload = archive.read(name)
                native_libs.append(
                    {
                        "abi": abi,
                        "name": soname,
                        "bytes": len(payload),
                        "sha256": sha256_bytes(payload),
                    }
                )
        apk_records.append(
            {
                "name": apk.name,
                "bytes": size,
                "sha256": sha256_file(apk),
                "no_internet": apk.name in no_internet_apks,
                "native_libraries": native_libs,
            }
        )

    unexpected_native = native_abis - ALLOWED_ABIS
    if unexpected_native:
        errors.append(f"unexpected packaged native ABI(s): {sorted(unexpected_native)}")

    bundle_records: list[dict[str, object]] = []
    bundles = sorted(bundle_root.rglob("*.aab")) if bundle_root.is_dir() else []
    if not bundles:
        errors.append(f"no release AAB found under {bundle_root}")
    for bundle in bundles:
        size = bundle.stat().st_size
        if size > AAB_MAX_BYTES:
            errors.append(f"{bundle.name} exceeds {AAB_MAX_BYTES} bytes: {size}")
        bundle_records.append(
            {
                "name": bundle.name,
                "bytes": size,
                "sha256": sha256_file(bundle),
            }
        )

    report: dict[str, object] = {
        "schema_version": 1,
        "status": "VALID" if not errors else "INVALID",
        "package": {
            "application_id": application_id,
            "compile_sdk": compile_sdk,
            "min_sdk": min_sdk,
            "target_sdk": target_sdk,
        },
        "abi": {
            "declared_splits": sorted(declared_abis),
            "packaged_native_abis": sorted(native_abis),
            "allowed": sorted(ALLOWED_ABIS),
        },
        "limits": {
            "apk_max_bytes": APK_MAX_BYTES,
            "aab_max_bytes": AAB_MAX_BYTES,
        },
        "dependencies": dependency_record,
        "apks": apk_records,
        "bundles": bundle_records,
        "validation_errors": errors,
    }
    return report, errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle-file", type=Path, required=True)
    parser.add_argument("--dependency-report", type=Path, required=True)
    parser.add_argument("--permission-report", type=Path, required=True)
    parser.add_argument("--apk-root", type=Path, required=True)
    parser.add_argument("--bundle-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report, errors = collect(
        gradle_file=args.gradle_file,
        dependency_report=args.dependency_report,
        permission_report=args.permission_report,
        apk_root=args.apk_root,
        bundle_root=args.bundle_root,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        for error in errors:
            print(f"compatibility error: {error}")
        return 1
    print(
        "compatibility report valid: "
        f"apks={len(report['apks'])} bundles={len(report['bundles'])} "
        f"abis={','.join(report['abi']['declared_splits'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
