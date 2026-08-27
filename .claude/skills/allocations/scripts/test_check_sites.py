import json
import subprocess
import sys
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).parents[4]
SCRIPT = Path(__file__).with_name("check_sites.py")


class CheckSitesJsonTest(unittest.TestCase):
    def test_outputs_structured_json(self):
        result = subprocess.run(
            [
                sys.executable,
                SCRIPT,
                "--baseline",
                "main",
                "HEAD..HEAD",
                "helloWorld[CIO]",
                "Pipeline.kt",
                "--json",
            ],
            cwd=REPOSITORY,
            check=True,
            capture_output=True,
            text=True,
        )

        output = json.loads(result.stdout)

        self.assertEqual(1, output["schemaVersion"])
        self.assertEqual("git", output["comparison"]["mode"])
        self.assertEqual("main", output["comparison"]["oldBaseline"])
        self.assertEqual("main", output["comparison"]["newBaseline"])
        self.assertEqual("helloWorld[CIO]", output["scenario"])
        self.assertEqual("Pipeline.kt", output["sourceFile"])
        self.assertEqual([], output["changes"])


if __name__ == "__main__":
    unittest.main()
