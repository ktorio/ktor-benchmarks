import json
import tempfile
import unittest
import zipfile
from datetime import datetime
from pathlib import Path

from fetch_teamcity_build import (
    FetchError,
    allocation_report_name,
    expected_allocation_files,
    install_allocations,
    parse_arguments,
    parse_build_id,
    parse_build_log,
    summarize_tests,
)


class ParseBuildIdTest(unittest.TestCase):
    def test_accepts_numeric_id(self):
        self.assertEqual(441472, parse_build_id("441472"))

    def test_accepts_build_url(self):
        self.assertEqual(
            441472,
            parse_build_id(
                "https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/441472"
            ),
        )

    def test_rejects_url_without_build_id(self):
        with self.assertRaisesRegex(ValueError, "cannot extract"):
            parse_build_id(
                "https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests"
            )

    def test_accepts_json_flag(self):
        arguments = parse_arguments(["441472", "--json"])

        self.assertTrue(arguments.json)


class ParseMetadataTest(unittest.TestCase):
    def test_parses_build_log(self):
        log = """
echo "Allocation baseline: $1 (TeamCity branch: $2)"
VCS revisions: 'Ktor_VCSCore' (Git): 'eeccdf4c50edab5cec585d49c234db5eb3dddb0a'
                 'Ktor_VCSKtorBenchmarks' (Git): 'a8de21b4259975d41a6ea27eca644432488e1ed5'
Allocation baseline: release-3.x (TeamCity branch: release/3.x)
"""

        self.assertEqual(
            {
                "targetBranch": "release/3.x",
                "allocationBaseline": "release-3.x",
                "ktorRevision": "eeccdf4c50edab5cec585d49c234db5eb3dddb0a",
                "benchmarksRevision": "a8de21b4259975d41a6ea27eca644432488e1ed5",
            },
            parse_build_log(log),
        )

    def test_parses_allocation_report_name(self):
        self.assertEqual(
            "client/helloWorld[CIO]",
            allocation_report_name(
                'benchmarks.ClientCallAllocationTest: helloWorldFootprint(String): '
                'benchmarks.ClientCallAllocationTest.helloWorldFootprint([1] "CIO")'
            ),
        )
        self.assertEqual(
            "fileResponse[Jetty]",
            allocation_report_name(
                'benchmarks.ServerCallAllocationTest: fileResponseFootprint(String): '
                'benchmarks.ServerCallAllocationTest.fileResponseFootprint([1] "Jetty")'
            ),
        )

    def test_derives_expected_files_from_teamcity_tests(self):
        tests = {
            "testOccurrence": [
                {
                    "name": 'benchmarks.ClientCallAllocationTest: helloWorldFootprint(String): '
                    'benchmarks.ClientCallAllocationTest.helloWorldFootprint([1] "CIO")'
                },
                {
                    "name": 'benchmarks.ServerCallAllocationTest: fileResponseFootprint(String): '
                    'benchmarks.ServerCallAllocationTest.fileResponseFootprint([1] "Jetty")'
                },
                {"name": "utils.benchmarks.AllocationToleranceTest.some test"},
            ]
        }

        self.assertEqual(
            {
                "client/helloWorld[CIO].json",
                "client/helloWorld[CIO]_sites.json",
                "fileResponse[Jetty].json",
                "fileResponse[Jetty]_sites.json",
            },
            expected_allocation_files(tests),
        )

    def test_rejects_missing_allocation_tests(self):
        with self.assertRaisesRegex(FetchError, "no allocation tests"):
            expected_allocation_files({"testOccurrence": []})

    def test_summarizes_failed_allocation_tests(self):
        tests = {
            "passed": 31,
            "failed": 2,
            "testOccurrence": [
                {
                    "name": 'benchmarks.ClientCallAllocationTest: helloWorldFootprint(String): '
                    'benchmarks.ClientCallAllocationTest.helloWorldFootprint([1] "CIO")',
                    "status": "FAILURE",
                },
                {
                    "name": "utils.benchmarks.AllocationToleranceTest.some test",
                    "status": "FAILURE",
                },
                {"name": "successful test", "status": "SUCCESS"},
            ],
        }

        summary = summarize_tests(tests)

        self.assertEqual(31, summary["passed"])
        self.assertEqual(2, summary["failed"])
        self.assertEqual(["client/helloWorld[CIO]"], summary["failedAllocations"])


class InstallAllocationsTest(unittest.TestCase):
    expected_files = {
        "client/helloWorld[CIO].json",
        "client/helloWorld[CIO]_sites.json",
        "fileResponse[Jetty].json",
        "fileResponse[Jetty]_sites.json",
    }

    def create_archive(self, root, files=None):
        archive = root / "new_allocations.zip"
        with zipfile.ZipFile(archive, "w") as zip_file:
            for path in files or self.expected_files:
                zip_file.writestr(path, json.dumps({"data": {}}))
        return archive

    def test_installs_valid_artifact_and_backs_up_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "allocations"
            output.mkdir()
            (output / "previous.txt").write_text("previous")
            archive = self.create_archive(root)

            backup = install_allocations(
                archive,
                output,
                self.expected_files,
                now=datetime(2026, 8, 20, 10, 24, 33),
            )

            self.assertEqual(root / "allocations.backup-20260820-102433", backup)
            self.assertEqual("previous", (backup / "previous.txt").read_text())
            actual = {
                path.relative_to(output).as_posix()
                for path in output.rglob("*.json")
            }
            self.assertEqual(self.expected_files, actual)
            self.assertEqual([], list(root.glob("allocations.staging-*")))

    def test_invalid_artifact_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "allocations"
            output.mkdir()
            marker = output / "previous.txt"
            marker.write_text("previous")
            archive = self.create_archive(root, {"client/helloWorld[CIO].json"})

            with self.assertRaisesRegex(FetchError, "invalid allocation artifact"):
                install_allocations(archive, output, self.expected_files)

            self.assertEqual("previous", marker.read_text())
            self.assertEqual([], list(root.glob("allocations.backup-*")))
            self.assertEqual([], list(root.glob("allocations.staging-*")))


if __name__ == "__main__":
    unittest.main()
