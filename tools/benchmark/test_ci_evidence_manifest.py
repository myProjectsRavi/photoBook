from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.benchmark.build_ci_evidence_manifest import collect_evidence


class CiEvidenceManifestTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.fixture = self.root / "fixtures" / "manifest-303.jsonl"
        self.fixture.parent.mkdir(parents=True)
        self.fixture.write_text('{"id":1}\n', encoding="utf-8")
        self.apks = self.root / "apks"
        self.apks.mkdir()
        (self.apks / "app-arm64-v8a-debug.apk").write_bytes(b"synthetic-apk")
        self.results = self.root / "results"
        self.results.mkdir()
        (self.results / "TEST-device.xml").write_text(
            '<testsuite tests="2" failures="0" errors="0" skipped="0"></testsuite>',
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def collect(self, fixture: Path | None = None, results: Path | None = None):
        return collect_evidence(
            checkout_sha="a" * 40,
            workflow_revision="workflow-blob-or-hash",
            workflow_ref="owner/repo/.github/workflows/autopilot-targeted-verify.yml@refs/heads/autopilot/test",
            api="35",
            abi="x86_64",
            avd_ram_mb=2048,
            fixture=fixture or self.fixture,
            test_results_root=results or self.results,
            apk_root=self.apks,
        )

    def test_valid_evidence_records_hashes_environment_and_test_count(self) -> None:
        manifest, errors = self.collect()

        self.assertEqual([], errors)
        self.assertEqual("VALID", manifest["status"])
        self.assertEqual(2, manifest["tests"]["count"])
        self.assertEqual(1, len(manifest["apks"]))
        self.assertEqual(64, len(manifest["apks"][0]["sha256"]))
        self.assertEqual(64, len(manifest["fixture"]["sha256"]))
        self.assertEqual(2048, manifest["emulator"]["avd_ram_mb"])

    def test_missing_fixture_fails_closed(self) -> None:
        manifest, errors = self.collect(fixture=self.root / "missing.jsonl")

        self.assertEqual("INVALID", manifest["status"])
        self.assertTrue(any("required fixture is missing" in error for error in errors))

    def test_zero_collected_tests_fails_closed(self) -> None:
        empty_results = self.root / "zero-results"
        empty_results.mkdir()
        (empty_results / "TEST-zero.xml").write_text(
            '<testsuite tests="0" failures="0" errors="0" skipped="0"></testsuite>',
            encoding="utf-8",
        )

        manifest, errors = self.collect(results=empty_results)

        self.assertEqual("INVALID", manifest["status"])
        self.assertTrue(any("zero test cases" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
