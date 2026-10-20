import json
import tempfile
import unittest
from pathlib import Path

from tools.benchmark.validate_frame_metrics import validate


def sampled(values):
    return {
        "P50": values[len(values) // 2],
        "P90": values[-2],
        "P95": values[-2],
        "P99": values[-1],
        "runs": [values[: len(values) // 2], values[len(values) // 2 :]],
    }


def benchmark(name, cpu, overrun=None):
    metrics = {"frameDurationCpuMs": sampled(cpu)}
    if overrun is not None:
        metrics["frameOverrunMs"] = sampled(overrun)
    return {"name": name, "sampledMetrics": metrics}


class FrameMetricValidationTest(unittest.TestCase):
    def write_data(self, root: Path, benchmarks):
        path = root / "com.photobook.app.baselineprofile-benchmarkData.json"
        path.write_text(json.dumps({"context": {}, "benchmarks": benchmarks}), encoding="utf-8")
        return path

    def all_interactions(self, cpu=None, overrun=None):
        cpu = cpu or ([8.0] * 38 + [12.0, 16.0])
        overrun = overrun or ([-8.0] * 38 + [-2.0, 0.0])
        names = (
            "e_gridScrollFrameTiming",
            "f_searchTypingAndResultsFrameTiming",
            "g_reelsVerticalSwipeFrameTiming",
        )
        return [benchmark(name, cpu, overrun) for name in names]

    def test_api35_good_frame_metrics_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_data(root, self.all_interactions())
            result = validate(root, api_level=35)
            self.assertTrue(result["passed"])
            self.assertEqual(3, len(result["benchmarks"]))

    def test_api35_positive_p95_overrun_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            overrun = [-8.0] * 36 + [2.0, 3.0, 6.0, 9.0]
            self.write_data(root, self.all_interactions(overrun=overrun))
            result = validate(root, api_level=35)
            self.assertFalse(result["passed"])
            self.assertTrue(any("frameOverrunMs P95" in item for item in result["failures"]))

    def test_api30_uses_cpu_metric_without_overrun(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            names = (
                "e_gridScrollFrameTiming",
                "f_searchTypingAndResultsFrameTiming",
                "g_reelsVerticalSwipeFrameTiming",
            )
            cpu = [7.0] * 38 + [12.0, 16.0]
            self.write_data(root, [benchmark(name, cpu) for name in names])
            result = validate(root, api_level=30)
            self.assertTrue(result["passed"])

    def test_missing_interaction_benchmark_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_data(root, self.all_interactions()[:2])
            with self.assertRaisesRegex(ValueError, "missing interaction benchmark"):
                validate(root, api_level=35)

    def test_most_complete_json_wins_over_stale_early_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stale = root / "early-benchmarkData.json"
            stale.write_text(
                json.dumps({"benchmarks": [{"name": "b_coldStartup", "sampledMetrics": {}}]}),
                encoding="utf-8",
            )
            complete = root / "final-benchmarkData.json"
            complete.write_text(
                json.dumps({"benchmarks": self.all_interactions()}),
                encoding="utf-8",
            )
            result = validate(root, api_level=35)
            self.assertTrue(result["passed"])
            self.assertTrue(result["source"].endswith("final-benchmarkData.json"))


if __name__ == "__main__":
    unittest.main()
