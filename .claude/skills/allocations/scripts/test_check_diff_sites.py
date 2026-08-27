import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from check_diff_sites import select_locations


REPOSITORY = Path(__file__).parents[4]
SCRIPT = Path(__file__).with_name("check_diff_sites.py")


class SelectLocationsTest(unittest.TestCase):
    def test_selects_failed_reports_and_known_variances(self):
        diff = {
            "reports": [
                {
                    "name": "failed[Engine]",
                    "passed": False,
                    "locations": [
                        {"sourceFile": "Failed.kt", "rawDelta": 100, "knownVariance": None},
                    ],
                },
                {
                    "name": "passed[Engine]",
                    "passed": True,
                    "locations": [
                        {"sourceFile": "Known.kt", "rawDelta": 80, "knownVariance": {}},
                        {"sourceFile": "Other.kt", "rawDelta": 70, "knownVariance": None},
                    ],
                },
            ]
        }

        selected = select_locations(diff, "failed-or-known-variance", 75)

        self.assertEqual(
            [("failed[Engine]", "Failed.kt"), ("passed[Engine]", "Known.kt")],
            [(report["name"], location["sourceFile"]) for report, location in selected],
        )


class CheckDiffSitesJsonTest(unittest.TestCase):
    def test_analyzes_selected_locations_from_diff_json(self):
        diff = {
            "schemaVersion": 1,
            "comparison": {
                "mode": "git",
                "oldBaseline": "main",
                "newBaseline": "main",
                "oldRevision": "HEAD",
                "newRevision": "HEAD",
                "defaultAllowedIncreaseRatio": 0.005,
            },
            "reports": [
                {
                    "name": "helloWorld[CIO]",
                    "passed": True,
                    "rawDelta": 0,
                    "locations": [
                        {"sourceFile": "Pipeline.kt", "rawDelta": 1, "knownVariance": None},
                    ],
                }
            ],
        }
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json") as file:
            json.dump(diff, file)
            file.flush()
            result = subprocess.run(
                [sys.executable, SCRIPT, file.name, "--json"],
                cwd=REPOSITORY,
                check=True,
                capture_output=True,
                text=True,
            )

        output = json.loads(result.stdout)
        self.assertEqual(1, output["schemaVersion"])
        self.assertEqual("all", output["selection"]["scope"])
        self.assertEqual(1, len(output["analyses"]))
        self.assertEqual("helloWorld[CIO]", output["analyses"][0]["scenario"])
        self.assertEqual("Pipeline.kt", output["analyses"][0]["sourceFile"])
        self.assertEqual([], output["analyses"][0]["changes"])


if __name__ == "__main__":
    unittest.main()
