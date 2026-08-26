"""Fetch allocation snapshots and metadata from a TeamCity build.

Usage:
  python fetch_teamcity_build.py BUILD [--output PATH] [--json]

BUILD may be a numeric TeamCity build ID or a Ktor allocation-build URL.
Run from the ktor-benchmarks repository root.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

DEFAULT_OUTPUT = Path("allocation-benchmark/build/allocations")
EXPECTED_BUILD_TYPE = "Ktor_AllocationTests"


class FetchError(RuntimeError):
    pass


def parse_build_id(value):
    if value.isdigit():
        return int(value)

    path = urlparse(value).path.rstrip("/")
    segment = path.rsplit("/", 1)[-1]
    if segment.isdigit():
        return int(segment)
    raise ValueError(f"cannot extract a TeamCity build ID from {value!r}")


def run_teamcity(*arguments):
    try:
        result = subprocess.run(
            ["teamcity", *map(str, arguments)],
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        raise FetchError("teamcity CLI not found") from None
    except subprocess.CalledProcessError as error:
        details = error.stderr.strip() or error.stdout.strip()
        suffix = f": {details}" if details else ""
        raise FetchError(f"teamcity {' '.join(map(str, arguments))} failed{suffix}") from None
    return result.stdout


def validate_staged_allocations(staging, expected_files):
    actual = {
        path.relative_to(staging).as_posix()
        for path in staging.rglob("*.json")
        if path.is_file()
    }
    if actual != expected_files:
        details = []
        if missing := expected_files - actual:
            details.append(f"missing: {', '.join(sorted(missing))}")
        if unexpected := actual - expected_files:
            details.append(f"unexpected: {', '.join(sorted(unexpected))}")
        raise FetchError("invalid allocation artifact (" + "; ".join(details) + ")")

    for path in staging.rglob("*.json"):
        try:
            with path.open() as file:
                json.load(file)
        except (OSError, json.JSONDecodeError) as error:
            raise FetchError(f"invalid JSON file {path}: {error}") from None


def extract_zip(archive, staging):
    staging_root = staging.resolve()
    try:
        with zipfile.ZipFile(archive) as zip_file:
            for member in zip_file.infolist():
                destination = (staging / member.filename).resolve()
                if destination != staging_root and staging_root not in destination.parents:
                    raise FetchError(f"artifact contains an unsafe path: {member.filename}")
            zip_file.extractall(staging)
    except zipfile.BadZipFile as error:
        raise FetchError(f"invalid allocation artifact {archive}: {error}") from None


def available_backup_path(output, now=None):
    timestamp = (now or datetime.now()).strftime("%Y%m%d-%H%M%S")
    candidate = output.with_name(f"{output.name}.backup-{timestamp}")
    index = 1
    while candidate.exists():
        candidate = output.with_name(f"{output.name}.backup-{timestamp}-{index}")
        index += 1
    return candidate


def install_allocations(archive, output, expected_files, now=None):
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f"{output.name}.staging-", dir=output.parent))
    backup = None
    try:
        extract_zip(archive, staging)
        validate_staged_allocations(staging, expected_files)

        if output.exists():
            if not output.is_dir():
                raise FetchError(f"output path is not a directory: {output}")
            backup = available_backup_path(output, now)
            output.rename(backup)
        try:
            staging.rename(output)
        except OSError:
            if backup is not None and not output.exists():
                backup.rename(output)
                backup = None
            raise
        return backup
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def parse_build_log(log):
    baseline_match = re.search(
        r"Allocation baseline: (main|release-\d+\.x) \(TeamCity branch: ([^)]+)\)",
        log,
    )
    property_match = re.search(r"-PallocationBaseline=(main|release-\d+\.x)\b", log)
    ktor_match = re.search(r"VCS revisions: 'Ktor_VCSCore'.*?: '([0-9a-f]{40})'", log)
    benchmarks_match = re.search(
        r"'Ktor_VCSKtorBenchmarks'.*?: '([0-9a-f]{40})'",
        log,
    )
    return {
        "targetBranch": baseline_match.group(2) if baseline_match else None,
        "allocationBaseline": (
            baseline_match.group(1)
            if baseline_match
            else property_match.group(1) if property_match else None
        ),
        "ktorRevision": ktor_match.group(1) if ktor_match else None,
        "benchmarksRevision": benchmarks_match.group(1) if benchmarks_match else None,
    }


def allocation_report_name(test_name):
    class_match = re.search(r"benchmarks\.(Client|Server)CallAllocationTest", test_name)
    scenario_match = re.search(r"\b(\w+)Footprint\(", test_name)
    engine_match = re.search(r'\[\d+\] "([^"]+)"', test_name)
    if not class_match or not scenario_match or not engine_match:
        return None
    prefix = "client/" if class_match.group(1) == "Client" else ""
    return f"{prefix}{scenario_match.group(1)}[{engine_match.group(1)}]"


def expected_allocation_files(tests):
    reports = {
        report_name
        for test in tests.get("testOccurrence", [])
        if (report_name := allocation_report_name(test.get("name", "")))
    }
    if not reports:
        raise FetchError("TeamCity returned no allocation tests")
    return {
        path
        for report_name in reports
        for path in (f"{report_name}.json", f"{report_name}_sites.json")
    }


def summarize_tests(tests):
    failed_allocations = [
        report_name
        for test in tests.get("testOccurrence", [])
        if test.get("status") == "FAILURE"
        if (report_name := allocation_report_name(test.get("name", "")))
    ]
    return {
        "passed": tests.get("passed", 0),
        "failed": tests.get("failed", 0),
        "failedAllocations": failed_allocations,
    }


def fetch_build(build_id, output):
    run_teamcity("auth", "status")

    build_text = run_teamcity("run", "view", build_id, "--json")
    tests_text = run_teamcity("run", "tests", build_id, "--json")
    log = run_teamcity("run", "log", build_id)
    try:
        build = json.loads(build_text)
        tests = json.loads(tests_text)
    except json.JSONDecodeError as error:
        raise FetchError(f"invalid JSON returned by TeamCity: {error}") from None

    if build.get("buildTypeId") != EXPECTED_BUILD_TYPE:
        raise FetchError(
            f"build {build_id} has type {build.get('buildTypeId')!r}; "
            f"expected {EXPECTED_BUILD_TYPE!r}"
        )

    teamcity_directory = output.parent / "teamcity" / str(build_id)
    teamcity_directory.mkdir(parents=True, exist_ok=True)
    (teamcity_directory / "build.json").write_text(build_text)
    (teamcity_directory / "tests.json").write_text(tests_text)
    (teamcity_directory / "build.log").write_text(log)

    archive = teamcity_directory / "new_allocations.zip"
    archive.unlink(missing_ok=True)
    run_teamcity(
        "run",
        "download",
        build_id,
        "--artifact",
        "new_allocations.zip",
        "-o",
        teamcity_directory,
    )
    if not archive.is_file():
        raise FetchError(f"TeamCity did not download {archive}")

    backup = install_allocations(archive, output, expected_allocation_files(tests))
    log_metadata = parse_build_log(log)
    if log_metadata["targetBranch"] is None:
        branch = build.get("branchName")
        if branch == "main" or (isinstance(branch, str) and re.fullmatch(r"release/\d+\.x", branch)):
            log_metadata["targetBranch"] = branch

    return {
        "schemaVersion": 1,
        "build": {
            "id": build_id,
            "url": build.get("webUrl"),
            "status": build.get("status"),
            "statusText": build.get("statusText"),
            "branch": build.get("branchName"),
            **log_metadata,
        },
        "tests": summarize_tests(tests),
        "paths": {
            "allocations": str(output),
            "previousAllocations": str(backup) if backup else None,
            "teamcity": str(teamcity_directory),
        },
    }


def print_text(result):
    build = result["build"]
    tests = result["tests"]
    paths = result["paths"]
    fields = [
        ("TeamCity build", build["id"]),
        ("URL", build["url"]),
        ("Status", build["status"]),
        ("Branch", build["branch"]),
        ("Target branch", build["targetBranch"]),
        ("Allocation baseline", build["allocationBaseline"]),
        ("Ktor revision", build["ktorRevision"]),
        ("Benchmarks revision", build["benchmarksRevision"]),
    ]
    for label, value in fields:
        print(f"{label}: {value or 'unknown'}")
    print(f"Tests: {tests['passed']} passed, {tests['failed']} failed")
    if tests["failedAllocations"]:
        print("Failed allocation tests:")
        for report_name in tests["failedAllocations"]:
            print(f"  {report_name}")
    print(f"Allocations: {paths['allocations']}")
    print(f"Previous allocations: {paths['previousAllocations'] or 'none'}")
    print(f"TeamCity data: {paths['teamcity']}")


def parse_arguments(arguments):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("build", help="TeamCity build ID or allocation-build URL")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json", action="store_true", help="output structured JSON")
    return parser.parse_args(arguments)


def main(arguments=None):
    arguments = parse_arguments(arguments)
    try:
        build_id = parse_build_id(arguments.build)
        result = fetch_build(build_id, arguments.output)
    except (FetchError, OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    if arguments.json:
        json.dump(result, sys.stdout, indent=2)
        print()
    else:
        print_text(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
