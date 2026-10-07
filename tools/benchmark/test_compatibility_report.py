from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.benchmark.build_compatibility_report import (
    AAB_MAX_BYTES,
    APK_MAX_BYTES,
    _declared_abis,
    collect,
)


class CompatibilityReportTest(unittest.TestCase):

    def fixture(self):
        tmp = tempfile.TemporaryDirectory()
        root = Path(tmp.name)
        gradle = root / "build.gradle.kts"
        gradle.write_text(
            """
tasks.register("extractAsset") {
    doLast {
        include("assets/mlkit_label_default_model/mobile_ica_8bit_with_metadata_tflite")
    }
}

android {
    compileSdk = 36
    defaultConfig {
        applicationId = "com.photobook.app"
        minSdk = 26
        targetSdk = 36
    }
    splits {
        abi {
            include("arm64-v8a", "armeabi-v7a")
        }
    }
}
""",
            encoding="utf-8",
        )
        deps = root / "deps.txt"
        deps.write_text("releaseRuntimeClasspath\n+--- example:dependency:1.0\n", encoding="utf-8")
        perms = root / "permissions.txt"
        apk_root = root / "apks"
        bundle_root = root / "bundles"
        apk_root.mkdir()
        bundle_root.mkdir()
        for abi in ("arm64-v8a", "armeabi-v7a"):
            apk = apk_root / f"app-{abi}-release.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(f"lib/{abi}/libdemo.so", f"native-{abi}".encode())
        perms.write_text(
            "\n".join(f"{p.name} no_internet=PASS" for p in sorted(apk_root.glob("*.apk"))) + "\n",
            encoding="utf-8",
        )
        with zipfile.ZipFile(bundle_root / "app-release.aab", "w") as archive:
            archive.writestr("base/manifest/AndroidManifest.xml", b"demo")
        return tmp, gradle, deps, perms, apk_root, bundle_root


    def test_nested_abi_block_parser(self):
        text = """
tasks.register("asset") {
    include("assets/not-an-abi")
}
android {
    splits {
        abi {
            isEnable = true
            include("arm64-v8a", "armeabi-v7a")
        }
    }
}
"""
        self.assertEqual({"arm64-v8a", "armeabi-v7a"}, _declared_abis(text))

    def test_valid_matrix_and_provenance_pass(self):
        tmp, gradle, deps, perms, apk_root, bundle_root = self.fixture()
        self.addCleanup(tmp.cleanup)

        report, errors = collect(
            gradle_file=gradle,
            dependency_report=deps,
            permission_report=perms,
            apk_root=apk_root,
            bundle_root=bundle_root,
        )

        self.assertEqual([], errors)
        self.assertEqual("VALID", report["status"])
        self.assertEqual(["arm64-v8a", "armeabi-v7a"], report["abi"]["declared_splits"])
        self.assertEqual(APK_MAX_BYTES, report["limits"]["apk_max_bytes"])
        self.assertEqual(AAB_MAX_BYTES, report["limits"]["aab_max_bytes"])
        self.assertTrue(all(apk["no_internet"] for apk in report["apks"]))


    def test_unrelated_include_does_not_override_abi_splits(self):
        tmp, gradle, deps, perms, apk_root, bundle_root = self.fixture()
        self.addCleanup(tmp.cleanup)

        report, errors = collect(
            gradle_file=gradle,
            dependency_report=deps,
            permission_report=perms,
            apk_root=apk_root,
            bundle_root=bundle_root,
        )

        self.assertEqual([], errors)
        self.assertEqual(
            ["arm64-v8a", "armeabi-v7a"],
            report["abi"]["declared_splits"],
        )

    def test_missing_no_internet_evidence_fails_closed(self):
        tmp, gradle, deps, perms, apk_root, bundle_root = self.fixture()
        self.addCleanup(tmp.cleanup)
        perms.write_text("", encoding="utf-8")

        report, errors = collect(
            gradle_file=gradle,
            dependency_report=deps,
            permission_report=perms,
            apk_root=apk_root,
            bundle_root=bundle_root,
        )

        self.assertEqual("INVALID", report["status"])
        self.assertTrue(any("no-INTERNET evidence missing" in error for error in errors))

    def test_unexpected_native_abi_fails_closed(self):
        tmp, gradle, deps, perms, apk_root, bundle_root = self.fixture()
        self.addCleanup(tmp.cleanup)
        apk = next(apk_root.glob("*arm64-v8a*.apk"))
        with zipfile.ZipFile(apk, "a") as archive:
            archive.writestr("lib/x86_64/libunexpected.so", b"unexpected")

        report, errors = collect(
            gradle_file=gradle,
            dependency_report=deps,
            permission_report=perms,
            apk_root=apk_root,
            bundle_root=bundle_root,
        )

        self.assertEqual("INVALID", report["status"])
        self.assertTrue(any("unexpected packaged native ABI" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
