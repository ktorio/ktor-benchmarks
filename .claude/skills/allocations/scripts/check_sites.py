"""
Show added/removed allocation call sites for a specific source file.

Modes:
  python check_sites.py OLD_COMMIT[..NEW_COMMIT] SCENARIO SOURCE_FILE
      Compare _sites.json from two git commits.
      NEW_COMMIT defaults to HEAD if omitted.

  python check_sites.py --local SCENARIO SOURCE_FILE
      Compare local build/allocations/ against the inferred local baseline.

Options:
  --baseline NAME     Compare one baseline family on both sides.
  --old-baseline NAME Select the old baseline for a cross-family comparison.
  --new-baseline NAME Select the new baseline for a cross-family comparison.
  --json              Output structured JSON instead of human-readable text.

Without baseline options, version tags and branch refs must identify the same baseline family.

SCENARIO: path to _sites.json relative to the selected baseline, without extension
          e.g. helloWorld[CIO]  or  client/streamingResponse[CIO]
SOURCE_FILE: source file name to inspect, e.g. ByteChannel.kt

Run from the ktor-benchmarks repository root.
"""

import argparse
import json
import sys

from site_analysis import analyze_grouped_sites, format_frames, group_by_file
from sources import (
    git_sources,
    infer_local_baseline,
    known_location_variance,
    local_sources,
    parse_baseline_arguments,
)


# ---------------------------------------------------------------------------
# Mode selection
# ---------------------------------------------------------------------------

parser = argparse.ArgumentParser(
    description=__doc__,
    formatter_class=argparse.RawDescriptionHelpFormatter,
)
parser.add_argument("items", nargs="*", help="comparison, scenario, and source file")
parser.add_argument("--local", action="store_true", help="compare local dumps against the baseline")
parser.add_argument("--baseline")
parser.add_argument("--old-baseline")
parser.add_argument("--new-baseline")
parser.add_argument("--json", action="store_true", help="output structured JSON")
arguments = parser.parse_args()

baseline_options = []
for option in ("baseline", "old_baseline", "new_baseline"):
    value = getattr(arguments, option)
    if value:
        baseline_options.extend((f"--{option.replace('_', '-')}", value))
try:
    _, old_baseline, new_baseline = parse_baseline_arguments(baseline_options)
except ValueError as error:
    parser.error(str(error))

expected_items = 2 if arguments.local else 3
if len(arguments.items) != expected_items:
    parser.error(
        "--local requires SCENARIO SOURCE_FILE"
        if arguments.local
        else "git mode requires OLD_COMMIT[..NEW_COMMIT] SCENARIO SOURCE_FILE"
    )

json_output = arguments.json

try:
    if arguments.local:
        if old_baseline != new_baseline:
            raise ValueError("local comparisons require one baseline family")
        baseline = old_baseline or infer_local_baseline()
        scenario, source_file = arguments.items
        old_source, new_source = local_sources(baseline)
        tolerance_source = old_source
        old_baseline_name = new_baseline_name = baseline
        baseline_description = baseline
        mode = "--local"
    else:
        comparison, scenario, source_file = arguments.items
        old_source, new_source = git_sources(
            comparison,
            old_baseline=old_baseline,
            new_baseline=new_baseline,
        )
        tolerance_source = new_source
        old_baseline_name = old_source.baseline
        new_baseline_name = new_source.baseline
        baseline_description = (
            old_source.baseline
            if old_source.baseline == new_source.baseline
            else f"{old_source.baseline} -> {new_source.baseline}"
        )
        mode = f"{old_source.ref}..{new_source.ref}"
except (FileNotFoundError, ValueError) as error:
    print(f"ERROR: {error}", file=sys.stderr)
    sys.exit(1)

variance = known_location_variance(
    tolerance_source.load_tolerances(),
    scenario,
    source_file,
)


# ---------------------------------------------------------------------------
# Analysis
# ---------------------------------------------------------------------------

try:
    new_sites = new_source.load_sites(scenario, required=True)
except FileNotFoundError as e:
    print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)
old_sites = old_source.load_sites(scenario, required=False)

old_by_file = group_by_file(old_sites)
new_by_file = group_by_file(new_sites)

try:
    changes = analyze_grouped_sites(old_by_file, new_by_file, source_file)
except ValueError as error:
    print(f"ERROR: {error}", file=sys.stderr)
    sys.exit(1)

analysis = {
    "schemaVersion": 1,
    "comparison": {
        "mode": "local" if mode == "--local" else "git",
        "oldBaseline": old_baseline_name,
        "newBaseline": new_baseline_name,
        "oldRevision": getattr(old_source, "ref", None),
        "newRevision": getattr(new_source, "ref", None),
    },
    "scenario": scenario,
    "sourceFile": source_file,
    "knownVariance": {
        "boundBytes": variance["knownVarianceBytes"],
        "reason": variance["reason"],
        "reference": variance.get("reference"),
    } if variance else None,
    "changes": changes,
}


def render_json():
    json.dump(analysis, sys.stdout, indent=2)
    print()


def render_text():
    if not changes:
        return

    print(f"\n{scenario} / {source_file}  {mode}  baseline={baseline_description}")
    if variance:
        print(f"  Known variance: up to {variance['knownVarianceBytes']:,} bytes")
        print(f"  Reason: {variance['reason']}")
        if variance.get("reference"):
            print(f"  See: {variance['reference']}")

    for change in changes:
        delta = change["rawDelta"]
        allocation_type = change["allocationType"]
        if change["kind"] == "added":
            frames = format_frames(change["newStackTrace"])
            print(f"  +{change['newSize']:,}  [{allocation_type}]  {frames}")
        elif change["kind"] == "removed":
            frames = format_frames(change["oldStackTrace"])
            print(f"  -{change['oldSize']:,}  [{allocation_type}]  {frames}")
        else:
            frames = format_frames(change["newStackTrace"])
            print(
                f"  {delta:+,} ({change['oldSize']:,} → {change['newSize']:,})  "
                f"[{allocation_type}]  {frames}"
            )


if json_output:
    render_json()
else:
    render_text()
