#!/usr/bin/env python3
"""Self-tests for Phase-0 host tooling. These tests never ship in the Android app."""

from __future__ import annotations

import binascii
import json
import os
import random
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
import zlib
from datetime import datetime, timezone
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import generate_media_fixtures as fixtures  # noqa: E402
import report_artifact_sizes as sizes  # noqa: E402


class FixtureGeneratorTest(unittest.TestCase):
    def test_single_scenario_cycle_covers_every_scenario(self) -> None:
        seen = {
            fixtures.scenario_for(index)
            for index in range(fixtures.SCENARIO_CYCLE_SIZE)
        }
        self.assertEqual(set(fixtures.SCENARIOS), seen)

    def test_partial_cycle_counts_are_valid(self) -> None:
        with tempfile.TemporaryDirectory() as output_dir:
            output = Path(output_dir)
            fixtures.generate(13, output, fixtures.DEFAULT_SEED, write_media=False)
            summary = json.loads((output / "summary-13.json").read_text(encoding="utf-8"))
            self.assertEqual(13, summary["count"])
            self.assertEqual(13, summary["scenarios"]["camera_general"])

    def test_large_corrupt_and_zero_byte_cases_are_reachable(self) -> None:
        self.assertEqual("large_photo", fixtures.scenario_for(98))
        self.assertEqual("corrupt_media", fixtures.scenario_for(99))
        self.assertEqual("zero_byte_media", fixtures.scenario_for(100))

    def test_generated_png_has_valid_chunks_crc_and_payload(self) -> None:
        payload = fixtures.make_png(42)
        self.assertEqual(b"\x89PNG\r\n\x1a\n", payload[:8])
        offset = 8
        idat_parts: list[bytes] = []
        dimensions: tuple[int, int] | None = None
        saw_iend = False

        while offset < len(payload):
            length = struct.unpack(">I", payload[offset:offset + 4])[0]
            chunk_type = payload[offset + 4:offset + 8]
            chunk_payload = payload[offset + 8:offset + 8 + length]
            expected_crc = struct.unpack(">I", payload[offset + 8 + length:offset + 12 + length])[0]
            actual_crc = binascii.crc32(chunk_type + chunk_payload) & 0xFFFFFFFF
            self.assertEqual(expected_crc, actual_crc)

            if chunk_type == b"IHDR":
                dimensions = struct.unpack(">II", chunk_payload[:8])
            elif chunk_type == b"IDAT":
                idat_parts.append(chunk_payload)
            elif chunk_type == b"IEND":
                saw_iend = True

            offset += 12 + length

        self.assertEqual((32, 32), dimensions)
        self.assertTrue(saw_iend)
        raw = zlib.decompress(b"".join(idat_parts))
        self.assertEqual(32 * (1 + 32 * 3), len(raw))

    def test_livestock_ground_truth_names_high_risk_subjects(self) -> None:
        required = {
            "cow",
            "buffalo",
            "goat",
            "sheep",
            "cattle",
            "bull",
            "horse",
            "hen",
            "rooster",
            "livestock",
        }
        actual = set(fixtures.SEMANTIC_SUBJECTS["livestock_negative"])
        self.assertTrue(required.issubset(actual), required - actual)

    def test_ci_smoke_corpus_instantiates_every_livestock_subject(self) -> None:
        with tempfile.TemporaryDirectory() as output_dir:
            output = Path(output_dir)
            fixtures.generate(303, output, fixtures.DEFAULT_SEED, write_media=False)
            summary = json.loads((output / "summary-303.json").read_text(encoding="utf-8"))
            generated = set(summary["semantic_subjects"]["livestock_negative"])
            expected = set(fixtures.SEMANTIC_SUBJECTS["livestock_negative"])
            self.assertEqual(expected, generated)

    def test_food_positive_contract_is_narrow(self) -> None:
        self.assertEqual(
            "food_candidate",
            fixtures.archive_expectation("cooked_food_positive")[0],
        )
        self.assertEqual(
            "food_candidate",
            fixtures.archive_expectation("fmcg_packaged_food_positive")[0],
        )
        for scenario in (
            "livestock_negative",
            "wildlife_negative",
            "raw_ingredient_negative",
        ):
            self.assertEqual("never_food", fixtures.archive_expectation(scenario)[0])

    def test_favorite_protection_overrides_archive_positive_scenarios(self) -> None:
        start = datetime(2026, 8, 16, 0, 0, tzinfo=timezone.utc)
        rng = random.Random(fixtures.DEFAULT_SEED)
        protected_positive_seen = False

        for index in range(5_000):
            record = fixtures.build_record(index, start, rng)
            if record.is_favorite:
                self.assertEqual("never_archive", record.expected_archive)
                self.assertEqual("favorites are protected", record.expected_archive_reason)
                if record.scenario in {
                    "screenshot_payment_positive",
                    "cooked_food_positive",
                    "fmcg_packaged_food_positive",
                }:
                    protected_positive_seen = True

        self.assertTrue(protected_positive_seen)

    def test_generation_is_deterministic_for_same_seed(self) -> None:
        with tempfile.TemporaryDirectory() as first_dir, tempfile.TemporaryDirectory() as second_dir:
            first = Path(first_dir)
            second = Path(second_dir)
            fixtures.generate(101, first, fixtures.DEFAULT_SEED, write_media=False)
            fixtures.generate(101, second, fixtures.DEFAULT_SEED, write_media=False)

            self.assertEqual(
                (first / "manifest-101.jsonl").read_bytes(),
                (second / "manifest-101.jsonl").read_bytes(),
            )
            summary = json.loads((first / "summary-101.json").read_text(encoding="utf-8"))
            self.assertEqual(101, summary["count"])
            self.assertEqual(fixtures.DEFAULT_SEED, summary["seed"])
            self.assertTrue(all(value > 0 for value in summary["scenarios"].values()))


class PhysicalRunnerWrapperTest(unittest.TestCase):
    def _prepare_fixture(self, root: Path) -> None:
        tools = root / "tools" / "benchmark"
        tools.mkdir(parents=True)
        wrapper = TOOLS_DIR / "run_phase5_physical_device.sh"
        (tools / wrapper.name).write_text(wrapper.read_text(encoding="utf-8"), encoding="utf-8")
        (tools / "run_phase3_device.sh").write_text(
            "#!/usr/bin/env bash\n"
            "touch delegated.txt\n"
            "printf 'mode=%s\\n' \"${REQUIRE_PHYSICAL:-unset}\"\n"
            "printf 'stress=%s\\n' \"${STRESS_ITERATIONS:-unset}\"\n"
            "printf 'argc=%d\\n' \"$#\"\n"
            "i=0\n"
            "for arg in \"$@\"; do\n"
            "  printf 'arg%d=%s\\n' \"$i\" \"$arg\"\n"
            "  i=$((i + 1))\n"
            "done\n",
            encoding="utf-8",
        )

    def test_wrapper_forces_physical_mode_and_forwards_arguments(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            root = Path(work_dir)
            self._prepare_fixture(root)
            env = os.environ.copy()
            env.pop("REQUIRE_PHYSICAL", None)
            env.pop("STRESS_ITERATIONS", None)
            result = subprocess.run(
                ["bash", "tools/benchmark/run_phase5_physical_device.sh", "alpha", "two words"],
                cwd=root,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                "mode=1\nstress=12\nargc=2\narg0=alpha\narg1=two words\n",
                result.stdout,
            )
            self.assertTrue((root / "delegated.txt").exists())

    def test_wrapper_accepts_stronger_stress_iterations(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            root = Path(work_dir)
            self._prepare_fixture(root)
            env = os.environ.copy()
            env.pop("REQUIRE_PHYSICAL", None)
            env["STRESS_ITERATIONS"] = "50"
            result = subprocess.run(
                ["bash", "tools/benchmark/run_phase5_physical_device.sh"],
                cwd=root,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("mode=1\nstress=50\nargc=0\n", result.stdout)
            self.assertTrue((root / "delegated.txt").exists())

    def test_wrapper_rejects_weakened_stress_iterations_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            root = Path(work_dir)
            self._prepare_fixture(root)
            for value in ("0", "-1", "01", "11", "twelve", "08", ""):
                delegated = root / "delegated.txt"
                delegated.unlink(missing_ok=True)
                env = os.environ.copy()
                env.pop("REQUIRE_PHYSICAL", None)
                env["STRESS_ITERATIONS"] = value
                result = subprocess.run(
                    ["bash", "tools/benchmark/run_phase5_physical_device.sh"],
                    cwd=root,
                    env=env,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(2, result.returncode, value)
                self.assertIn("STRESS_ITERATIONS", result.stderr)
                self.assertFalse(delegated.exists(), value)

    def test_wrapper_rejects_conflicting_override_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            root = Path(work_dir)
            self._prepare_fixture(root)
            for value in ("true", "yes", "01", "0", "-1", "2", ""):
                delegated = root / "delegated.txt"
                delegated.unlink(missing_ok=True)
                env = os.environ.copy()
                env["REQUIRE_PHYSICAL"] = value
                result = subprocess.run(
                    ["bash", "tools/benchmark/run_phase5_physical_device.sh"],
                    cwd=root,
                    env=env,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(2, result.returncode, value)
                self.assertIn("requires REQUIRE_PHYSICAL=1", result.stderr)
                self.assertFalse(delegated.exists(), value)


class ArtifactSizeClassifierTest(unittest.TestCase):
    def test_tflite_is_reported_as_model_not_generic_asset(self) -> None:
        self.assertEqual(
            "models",
            sizes.category_for("base/assets/photobook/food_live_label_model.tflite"),
        )
        self.assertEqual(
            "models",
            sizes.category_for("assets/photobook/food_live_label_model.tflite"),
        )

    def test_common_artifact_entries_are_classified(self) -> None:
        self.assertEqual("dex", sizes.category_for("base/dex/classes.dex"))
        self.assertEqual("lib", sizes.category_for("base/lib/arm64-v8a/libfoo.so"))
        self.assertEqual("resources", sizes.category_for("resources.arsc"))

    def test_inspect_artifact_reports_model_and_code_buckets(self) -> None:
        with tempfile.TemporaryDirectory() as output_dir:
            artifact = Path(output_dir) / "synthetic-release.apk"
            with zipfile.ZipFile(artifact, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("classes.dex", b"DEX" * 100)
                archive.writestr("lib/arm64-v8a/libfoo.so", b"LIB" * 100)
                archive.writestr(
                    "assets/photobook/food_live_label_model.tflite",
                    b"MODEL" * 100,
                )
                archive.writestr("resources.arsc", b"RES" * 100)

            report = sizes.inspect_artifact(artifact)
            categories = report["compressed_categories_bytes"]
            self.assertGreater(categories["dex"], 0)
            self.assertGreater(categories["lib"], 0)
            self.assertGreater(categories["models"], 0)
            self.assertGreater(categories["resources"], 0)
            self.assertEqual(artifact.stat().st_size, report["artifact_bytes"])


if __name__ == "__main__":
    unittest.main()
