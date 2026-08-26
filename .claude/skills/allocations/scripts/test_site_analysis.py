import unittest

from site_analysis import analyze_grouped_sites, group_by_file


class AnalyzeGroupedSitesTest(unittest.TestCase):
    def test_preserves_allocation_type_and_old_and_new_stacks(self):
        old_stack = "File.kt:10 allocate, Caller.kt:20 call"
        new_stack = "File.kt:10 allocate, Caller.kt:21 call"
        old_sites = [
            {"name": "[B", "stackTrace": old_stack, "totalSize": 10},
            {"name": "Removed", "stackTrace": "File.kt:30 old", "totalSize": 5},
        ]
        new_sites = [
            {"name": "[B", "stackTrace": new_stack, "totalSize": 15},
            {"name": "Added", "stackTrace": "File.kt:40 new", "totalSize": 4},
        ]

        changes = analyze_grouped_sites(
            group_by_file(old_sites),
            group_by_file(new_sites),
            "File.kt",
        )

        self.assertEqual(["changed", "added", "removed"], [change["kind"] for change in changes])
        changed = changes[0]
        self.assertEqual("[B", changed["allocationType"])
        self.assertEqual(5, changed["rawDelta"])
        self.assertEqual(old_stack, changed["oldStackTrace"])
        self.assertEqual(new_stack, changed["newStackTrace"])

    def test_keeps_different_allocation_types_at_the_same_stack_separate(self):
        stack = "File.kt:10 allocate, Caller.kt:20 call"
        changes = analyze_grouped_sites(
            group_by_file([]),
            group_by_file(
                [
                    {"name": "[B", "stackTrace": stack, "totalSize": 10},
                    {"name": "okio.Segment", "stackTrace": stack, "totalSize": 2},
                ]
            ),
            "File.kt",
        )

        self.assertEqual({"[B", "okio.Segment"}, {change["allocationType"] for change in changes})


if __name__ == "__main__":
    unittest.main()
