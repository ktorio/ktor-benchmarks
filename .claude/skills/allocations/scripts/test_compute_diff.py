import json
import subprocess
import sys
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).parents[4]
SCRIPT = Path(__file__).with_name("compute_diff.py")


class ComputeDiffJsonTest(unittest.TestCase):
    def test_outputs_structured_json(self):
        result = subprocess.run(
            [
                sys.executable,
                SCRIPT,
                "--baseline",
                "main",
                "HEAD..HEAD",
                "--json",
            ],
            cwd=REPOSITORY,
            check=True,
            capture_output=True,
            text=True,
        )

        output = json.loads(result.stdout)

        self.assertEqual(json.dumps(output, separators=(",", ":")), result.stdout.rstrip("\n"))
        self.assertEqual(1, output["schemaVersion"])
        self.assertEqual("git", output["comparison"]["mode"])
        self.assertEqual("main", output["comparison"]["oldBaseline"])
        self.assertEqual("main", output["comparison"]["newBaseline"])
        self.assertTrue(output["reports"])
        for report in output["reports"]:
            self.assertEqual(report["oldTotal"], report["newTotal"])
            self.assertEqual(0, report["rawDelta"])
            self.assertTrue(report["passed"])
            self.assertEqual([], report["locations"])


if __name__ == "__main__":
    unittest.main()
