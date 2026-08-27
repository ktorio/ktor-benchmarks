"""
Inspect allocation call sites for every selected location in compute_diff.py JSON output.

Usage:
  python check_diff_sites.py DIFF_JSON [options]

Options:
  --scope all                         Inspect all changed locations (default).
  --scope failed                      Inspect locations in failed reports.
  --scope failed-or-known-variance    Inspect failed reports and configured known variances.
  --threshold N                       Require |location delta| >= N bytes (default: 0).
  --json                              Output structured JSON instead of human-readable text.

Run from the ktor-benchmarks repository root.
"""

import argparse
import json
import sys
from pathlib import Path

from site_analysis import analyze_grouped_sites, format_frames, group_by_file
from sources import git_sources, local_sources


class AnalysisError(Exception):
    pass


def parse_arguments(arguments):
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("diff", type=Path, help="compute_diff.py JSON output")
    parser.add_argument(
        "--scope",
        choices=("all", "failed", "failed-or-known-variance"),
        default="all",
    )
    parser.add_argument("--threshold", type=int, default=0, metavar="N")
    parser.add_argument("--json", action="store_true", help="output structured JSON")
    parsed = parser.parse_args(arguments)
    if parsed.threshold < 0:
        parser.error("--threshold must be non-negative")
    return parsed


def load_diff(path):
    try:
        with path.open() as file:
            result = json.load(file)
    except OSError as error:
        raise AnalysisError(f"cannot read {path}: {error}") from None
    except json.JSONDecodeError as error:
        raise AnalysisError(f"invalid JSON in {path}: {error}") from None

    if not isinstance(result, dict) or result.get("schemaVersion") != 1:
        raise AnalysisError(f"{path} is not supported compute_diff.py JSON")
    if not isinstance(result.get("comparison"), dict) or not isinstance(result.get("reports"), list):
        raise AnalysisError(f"{path} is missing comparison or reports")
    return result


def resolve_sources(comparison):
    mode = comparison.get("mode")
    old_baseline = comparison.get("oldBaseline")
    new_baseline = comparison.get("newBaseline")
    if not isinstance(old_baseline, str) or not isinstance(new_baseline, str):
        raise AnalysisError("diff comparison is missing allocation baselines")

    if mode == "local":
        if old_baseline != new_baseline:
            raise AnalysisError("local comparisons require one baseline family")
        return local_sources(old_baseline)

    if mode == "git":
        old_revision = comparison.get("oldRevision")
        new_revision = comparison.get("newRevision")
        if not isinstance(old_revision, str) or not isinstance(new_revision, str):
            raise AnalysisError("git comparison is missing revisions")
        return git_sources(
            f"{old_revision}..{new_revision}",
            old_baseline=old_baseline,
            new_baseline=new_baseline,
        )

    raise AnalysisError(f"unsupported comparison mode: {mode!r}")


def select_locations(diff, scope, threshold):
    selected = []
    for report in diff["reports"]:
        if not isinstance(report, dict):
            raise AnalysisError("diff contains an invalid report")
        report_failed = report.get("passed") is False
        locations = report.get("locations")
        if not isinstance(report.get("name"), str) or not isinstance(locations, list):
            raise AnalysisError("diff report is missing name or locations")
        for location in locations:
            if not isinstance(location, dict):
                raise AnalysisError(f"invalid location in {report['name']}")
            source_file = location.get("sourceFile")
            raw_delta = location.get("rawDelta")
            if not isinstance(source_file, str) or isinstance(raw_delta, bool) or not isinstance(raw_delta, int):
                raise AnalysisError(f"invalid location in {report['name']}")
            known_variance = location.get("knownVariance")
            if scope == "failed" and not report_failed:
                continue
            if scope == "failed-or-known-variance" and not report_failed and known_variance is None:
                continue
            if abs(raw_delta) < threshold:
                continue
            selected.append((report, location))
    return selected


def analyze(diff, scope, threshold):
    old_source, new_source = resolve_sources(diff["comparison"])
    selected = select_locations(diff, scope, threshold)
    selected_by_report = {}
    for report, location in selected:
        selected_by_report.setdefault(report["name"], (report, []))[1].append(location)

    analyses = []
    for scenario, (report, locations) in selected_by_report.items():
        try:
            new_sites = new_source.load_sites(scenario, required=True)
            old_sites = old_source.load_sites(scenario, required=False)
        except FileNotFoundError as error:
            raise AnalysisError(str(error)) from None
        old_by_file = group_by_file(old_sites)
        new_by_file = group_by_file(new_sites)

        for location in locations:
            source_file = location["sourceFile"]
            try:
                changes = analyze_grouped_sites(old_by_file, new_by_file, source_file)
            except ValueError as error:
                raise AnalysisError(f"{scenario}: {error}") from None
            analyses.append(
                {
                    "scenario": scenario,
                    "sourceFile": source_file,
                    "reportPassed": report["passed"],
                    "reportRawDelta": report["rawDelta"],
                    "locationRawDelta": location["rawDelta"],
                    "knownVariance": location.get("knownVariance"),
                    "changes": changes,
                }
            )

    return {
        "schemaVersion": 1,
        "comparison": diff["comparison"],
        "selection": {"scope": scope, "thresholdBytes": threshold},
        "analyses": analyses,
    }


def render_json(result):
    json.dump(result, sys.stdout, indent=2)
    print()


def render_text(result):
    comparison = result["comparison"]
    baseline = comparison["oldBaseline"]
    if baseline != comparison["newBaseline"]:
        baseline = f"{baseline} -> {comparison['newBaseline']}"
    print(
        f"mode={comparison['mode']}  baseline={baseline}  "
        f"scope={result['selection']['scope']}  "
        f"threshold={result['selection']['thresholdBytes']}"
    )
    for analysis in result["analyses"]:
        print(
            f"\n{analysis['scenario']} / {analysis['sourceFile']}  "
            f"location delta {analysis['locationRawDelta']:+,}"
        )
        variance = analysis["knownVariance"]
        if variance:
            print(f"  Known variance: up to {variance['boundBytes']:,} bytes")
            print(f"  Reason: {variance['reason']}")
            if variance.get("reference"):
                print(f"  See: {variance['reference']}")
        if not analysis["changes"]:
            print("  No call-site changes")
            continue
        for change in analysis["changes"]:
            stack_trace = change["newStackTrace"] or change["oldStackTrace"]
            frames = format_frames(stack_trace)
            if change["kind"] == "added":
                size = f"+{change['newSize']:,}"
            elif change["kind"] == "removed":
                size = f"-{change['oldSize']:,}"
            else:
                size = (
                    f"{change['rawDelta']:+,} "
                    f"({change['oldSize']:,} → {change['newSize']:,})"
                )
            print(f"  {size}  [{change['allocationType']}]  {frames}")


def main(arguments=None):
    arguments = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    try:
        diff = load_diff(arguments.diff)
        result = analyze(diff, arguments.scope, arguments.threshold)
    except (AnalysisError, FileNotFoundError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    if arguments.json:
        render_json(result)
    else:
        render_text(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
