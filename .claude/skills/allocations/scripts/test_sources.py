import unittest

from sources import validate_tolerances


class ValidateTolerancesTest(unittest.TestCase):
    def test_accepts_valid_metadata(self):
        metadata = {
            "defaultAllowedIncreaseRatio": 0.005,
            "reports": {
                "client/streamingResponse[OkHttp]": {
                    "locations": {
                        "OkHttpEngine.kt": {
                            "knownVarianceBytes": 278528,
                            "reason": "Known scheduler variance",
                            "reference": "allocation-benchmark/3.5.0.md",
                        }
                    }
                }
            },
        }

        self.assertIs(metadata, validate_tolerances(metadata))

    def test_rejects_missing_default_ratio(self):
        with self.assertRaisesRegex(ValueError, "defaultAllowedIncreaseRatio"):
            validate_tolerances({"reports": {}})

    def test_rejects_negative_default_ratio(self):
        with self.assertRaisesRegex(ValueError, "defaultAllowedIncreaseRatio"):
            validate_tolerances({"defaultAllowedIncreaseRatio": -0.1})

    def test_rejects_negative_variance(self):
        with self.assertRaisesRegex(ValueError, "knownVarianceBytes"):
            validate_tolerances(
                {
                    "defaultAllowedIncreaseRatio": 0.005,
                    "reports": {
                        "scenario[Engine]": {
                            "locations": {
                                "Source.kt": {
                                    "knownVarianceBytes": -1,
                                    "reason": "Known variance",
                                }
                            }
                        }
                    },
                }
            )

    def test_rejects_blank_reason(self):
        with self.assertRaisesRegex(ValueError, "reason"):
            validate_tolerances(
                {
                    "defaultAllowedIncreaseRatio": 0.005,
                    "reports": {
                        "scenario[Engine]": {
                            "locations": {
                                "Source.kt": {
                                    "knownVarianceBytes": 1,
                                    "reason": " ",
                                }
                            }
                        }
                    },
                }
            )


if __name__ == "__main__":
    unittest.main()
