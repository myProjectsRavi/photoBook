#!/usr/bin/env python3
"""Self-tests for Phase-0 host tooling. These tests never ship in the Android app."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import generate_media_fixtures as fixtures  # noqa: E402
import report_artifact_sizes as sizes  # noqa: E402


class FixtureGeneratorTest(unittest.TestCase):
    def test_single_101_record_cycle_covers_every_scenario(self) -> None:
        seen = {fixtures.scenario_for(index) for index in range(101)}
        self.assertEqual(set(fixtures.SCENARIOS), seen)

    def test_large_corrupt_and_zero_byte_cases_are_reachable(self) -> None:
        self.assertEqual("large_photo", fixtures.scenario_for(98))
        self.assertEqual("corrupt_media", fixtures.scenario_for(99))
        self.assertEqual("zero_byte_media", fixtures.scenario_for(100))

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


if __name__ == "__main__":
    unittest.main()
