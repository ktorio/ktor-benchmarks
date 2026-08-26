"""
Compute per-file allocation diffs across all discovered scenarios.

Modes:
  python compute_diff.py OLD_COMMIT[..NEW_COMMIT]
      Compare allocation dumps from two git commits.
      NEW_COMMIT defaults to HEAD if omitted.

  python compute_diff.py --local
      Compare local build/allocations/ against the inferred local baseline.

Options:
  --baseline NAME     Compare one baseline family on both sides.
  --old-baseline NAME Select the old baseline for a cross-family comparison.
  --new-baseline NAME Select the new baseline for a cross-family comparison.
  --threshold N       Hide per-file entries with |delta| < N bytes (default: 50).
                  Use --threshold 0 to show all entries.
                  Affects only changed sites, added and removed sites are always shown.
  --json              Output structured JSON instead of human-readable text.

Without baseline options, version tags and branch refs must identify the same baseline family.
Run from the ktor-benchmarks repository root.
"""

import argparse
import json
import math
import re
import subprocess
import sys

from sources import (
    git_sources,
    infer_local_baseline,
    known_location_variance,
    local_sources,
    parse_baseline_arguments,
)

SUBDIRS = {"server": "", "client": "client"}


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

def non_negative_integer(value):
    try:
        result = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError("must be a non-negative integer") from None
    if result < 0:
        raise argparse.ArgumentTypeError("must be non-negative")
    return result


parser = argparse.ArgumentParser(
    description=__doc__,
    formatter_class=argparse.RawDescriptionHelpFormatter,
)
parser.add_argument("comparison", nargs="?", help="OLD_COMMIT[..NEW_COMMIT]")
parser.add_argument("--local", action="store_true", help="compare local dumps against the baseline")
parser.add_argument("--baseline")
parser.add_argument("--old-baseline")
parser.add_argument("--new-baseline")
parser.add_argument("--threshold", type=non_negative_integer, default=50, metavar="N")
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

if arguments.local and arguments.comparison:
    parser.error("comparison cannot be combined with --local")
if not arguments.local and not arguments.comparison:
    parser.error("comparison or --local is required")

json_output = arguments.json
threshold = arguments.threshold

try:
    if arguments.local:
        if old_baseline != new_baseline:
            raise ValueError("local comparisons require one baseline family")
        baseline = old_baseline or infer_local_baseline()
        old_source, new_source = local_sources(baseline)
        tolerance_source = old_source
        old_baseline_name = new_baseline_name = baseline
        baseline_description = baseline
        mode = "--local"
    else:
        old_source, new_source = git_sources(
            arguments.comparison,
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

tolerances = tolerance_source.load_tolerances()
allowed_ratio = tolerances.get("defaultAllowedIncreaseRatio", 0.0)


# ---------------------------------------------------------------------------
# Diff computation
# ---------------------------------------------------------------------------

def extract(data):
    return {k: v["locationSize"] for k, v in data["data"].items()}


def try_load(source, subdir, fname):
    try:
        return extract(source.load(subdir, fname))
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None


def analyze_report(section, engine, data):
    suite, scenario = section.split("/", 1)
    default_tolerance = math.floor(data["old_total"] * allowed_ratio + 0.5)
    apply_known_variance = data["delta"] > default_tolerance
    known_variance_consumed = 0
    locations = []
    for delta, source_file, old_size, new_size in data["diffs"]:
        variance = known_location_variance(tolerances, data["report_name"], source_file)
        relevant_variance = bool(variance and (delta < 0 or apply_known_variance))
        consumed_bytes = 0
        if relevant_variance and delta > 0:
            consumed_bytes = min(delta, variance["knownVarianceBytes"])
            known_variance_consumed += consumed_bytes
        location = {
            "sourceFile": source_file,
            "oldSize": old_size,
            "newSize": new_size,
            "rawDelta": delta,
        }
        if variance:
            location["knownVariance"] = {
                "boundBytes": variance["knownVarianceBytes"],
                "reason": variance["reason"],
                "reference": variance.get("reference"),
                "relevant": relevant_variance,
                "consumedBytes": consumed_bytes,
                "exceedsBound": abs(delta) > variance["knownVarianceBytes"],
            }
        locations.append(location)
    effective_delta = data["delta"] - known_variance_consumed
    unexpected_increase = max(effective_delta - default_tolerance, 0)
    return {
        "name": data["report_name"],
        "suite": suite,
        "scenario": scenario,
        "engine": engine,
        "oldTotal": data["old_total"],
        "newTotal": data["new_total"],
        "rawDelta": data["delta"],
        "defaultTolerance": default_tolerance,
        "knownVarianceConsumed": known_variance_consumed,
        "effectiveDelta": effective_delta,
        "unexpectedIncrease": unexpected_increase,
        "passed": unexpected_increase == 0,
        "locations": locations,
    }


results = {}
for side, subdir in SUBDIRS.items():
    all_fnames = set(old_source.list_files(subdir)) | set(new_source.list_files(subdir))
    for fname in sorted(all_fnames):
        m = re.match(r"^(.+)\[(.+)\]\.json$", fname)
        if not m:
            continue
        scenario, engine = m.group(1), m.group(2)
        key = f"{side}/{scenario}"
        old = try_load(old_source, subdir, fname)
        new = try_load(new_source, subdir, fname)
        if old is None and new is None:
            continue
        old = old or {}
        new = new or {}
        old_total = sum(old.values())
        new_total = sum(new.values())
        diffs = []
        for k in set(old) | set(new):
            d = new.get(k, 0) - old.get(k, 0)
            if d != 0:
                diffs.append((d, k, old.get(k, 0), new.get(k, 0)))
        diffs.sort(key=lambda x: (int(x[0] < 0), -abs(x[0])))
        report_name = f"{subdir}/{scenario}[{engine}]" if subdir else f"{scenario}[{engine}]"
        results.setdefault(key, {})[engine] = {
            "report_name": report_name,
            "old_total": old_total,
            "new_total": new_total,
            "delta": new_total - old_total,
            "diffs": diffs,  # list of (delta, filename, old, new)
        }

reports = [
    analyze_report(section, engine, data)
    for section, engines in results.items()
    for engine, data in engines.items()
]
comparison = {
    "mode": "local" if mode == "--local" else "git",
    "oldBaseline": old_baseline_name,
    "newBaseline": new_baseline_name,
    "oldRevision": getattr(old_source, "ref", None),
    "newRevision": getattr(new_source, "ref", None),
    "defaultAllowedIncreaseRatio": allowed_ratio,
}


def render_json():
    json.dump(
        {"schemaVersion": 1, "comparison": comparison, "reports": reports},
        sys.stdout,
        separators=(",", ":"),
    )
    print()


def render_text():
    print(
        f"{mode}  baseline={baseline_description}  --threshold={threshold}  "
        f"--allowed-increase={allowed_ratio:.2%}"
    )
    current_section = None
    for report in reports:
        section = f"{report['suite']}/{report['scenario']}"
        if section != current_section:
            print(f"\n{section}")
            current_section = section

        delta = report["rawDelta"]
        old_kb = report["oldTotal"] / 1024
        new_kb = report["newTotal"] / 1024
        print(f"  {report['engine']}: {old_kb:.2f} KB → {new_kb:.2f} KB  ({delta:+,})")
        hidden_count = 0
        hidden_sum = 0
        for location in report["locations"]:
            source_file = location["sourceFile"]
            old_size = location["oldSize"]
            new_size = location["newSize"]
            location_delta = location["rawDelta"]
            variance = location.get("knownVariance")
            show_known_variance = bool(variance and variance["relevant"])
            if show_known_variance:
                variance_bytes = variance["boundBytes"]
                annotation = f"  [known variance up to {variance_bytes:,} bytes"
                if variance["exceedsBound"]:
                    annotation += f"; exceeds bound by {abs(location_delta) - variance_bytes:,}"
                annotation += "]"
            else:
                annotation = ""

            if old_size == 0:
                print(f"    +{new_size:,}  {source_file}{annotation}")
            elif new_size == 0:
                print(f"    -{old_size:,}  {source_file}{annotation}")
            elif abs(location_delta) < threshold and not show_known_variance:
                hidden_count += 1
                hidden_sum += location_delta
            else:
                print(
                    f"    {location_delta:+,} ({old_size:,} → {new_size:,})  "
                    f"{source_file}{annotation}"
                )

            if show_known_variance:
                print(f"      {variance['reason']}")
                if variance["reference"]:
                    print(f"      See: {variance['reference']}")

        if hidden_count:
            print(f"    ({hidden_count} below threshold, net {hidden_sum:+,})")
        if report["knownVarianceConsumed"]:
            print(
                f"    Known variance consumed: {report['knownVarianceConsumed']:,} bytes; "
                f"effective delta: {report['effectiveDelta']:+,}; "
                f"default tolerance: {report['defaultTolerance']:,}; "
                f"unexpected increase: {report['unexpectedIncrease']:,}"
            )
        elif report["unexpectedIncrease"]:
            print(
                f"    Default tolerance: {report['defaultTolerance']:,} bytes; "
                f"unexpected increase: {report['unexpectedIncrease']:,}"
            )


if json_output:
    render_json()
else:
    render_text()
