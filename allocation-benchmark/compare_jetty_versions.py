#!/usr/bin/env python3

import argparse
import json
import math
import statistics
import subprocess
from pathlib import Path

OLD_VERSION = "12.0.35"
NEW_VERSION = "12.1.12"
T_CRITICAL_95 = (
    0.0,
    12.706,
    4.303,
    3.182,
    2.776,
    2.571,
    2.447,
    2.365,
    2.306,
    2.262,
    2.228,
    2.201,
    2.179,
    2.160,
    2.145,
    2.131,
    2.120,
    2.110,
    2.101,
    2.093,
    2.086,
    2.080,
    2.074,
    2.069,
    2.064,
    2.060,
    2.056,
    2.052,
    2.048,
    2.045,
    2.042,
)

PROJECT_DIRECTORY = Path(__file__).resolve().parent
RESULT_DIRECTORY = PROJECT_DIRECTORY / "build/results/jmh"
DEFAULT_OUTPUT = RESULT_DIRECTORY / "jetty-version-comparison.json"


def parse_arguments():
    parser = argparse.ArgumentParser(
        description="Run alternating Jetty JMH trials and calculate paired confidence intervals."
    )
    parser.add_argument("--pairs", type=int, default=10, help="number of paired trials (default: 10)")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def run_trial(version, result_name):
    subprocess.run(
        [
            str(PROJECT_DIRECTORY / "gradlew"),
            "jmh",
            "--rerun",
            "-PjmhForks=1",
            f"-PjettyVersion={version}",
            f"-PjmhResultName={result_name}",
        ],
        cwd=PROJECT_DIRECTORY,
        check=True,
    )

    benchmark_results = json.loads((RESULT_DIRECTORY / f"{result_name}.json").read_text())
    result = next(
        item for item in benchmark_results
        if item["benchmark"].endswith("JettyServerBenchmark.fileResponse")
    )
    return {
        "timeMilliseconds": result["primaryMetric"]["score"],
        "serverAllocatedBytes": result["secondaryMetrics"]["server.alloc.rate.norm"]["score"],
        "processAllocatedBytes": result["secondaryMetrics"]["·gc.alloc.rate.norm"]["score"],
    }


def confidence_interval(values):
    mean = statistics.fmean(values)
    if len(values) < 2:
        return [math.nan, math.nan]
    degrees_of_freedom = len(values) - 1
    critical_value = (
        T_CRITICAL_95[degrees_of_freedom]
        if degrees_of_freedom < len(T_CRITICAL_95)
        else 1.96
    )
    margin = critical_value * statistics.stdev(values) / math.sqrt(len(values))
    return [mean - margin, mean + margin]


def summarize(pairs, metric):
    old_values = [pair["results"][OLD_VERSION][metric] for pair in pairs]
    new_values = [pair["results"][NEW_VERSION][metric] for pair in pairs]
    differences = [new - old for old, new in zip(old_values, new_values)]
    return {
        "oldMean": statistics.fmean(old_values),
        "newMean": statistics.fmean(new_values),
        "differenceMean": statistics.fmean(differences),
        "differenceConfidenceInterval95": confidence_interval(differences),
    }


def write_results(output, pairs):
    result = {
        "schemaVersion": 1,
        "versions": {"old": OLD_VERSION, "new": NEW_VERSION},
        "pairs": pairs,
    }
    if len(pairs) >= 2:
        time_summary = summarize(pairs, "timeMilliseconds")
        time_summary["unit"] = "ms/op"
        server_summary = summarize(pairs, "serverAllocatedBytes")
        server_summary["unit"] = "B/op"
        process_summary = summarize(pairs, "processAllocatedBytes")
        process_summary["unit"] = "B/op"
        result["summary"] = {
            "confidenceIntervalMethod": "paired Student's t, 95%",
            "timeMilliseconds": time_summary,
            "serverAllocatedBytes": server_summary,
            "processAllocatedBytes": process_summary,
        }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")


def main():
    arguments = parse_arguments()
    if arguments.pairs < 2:
        raise ValueError("At least two pairs are required for a confidence interval")

    pairs = []
    for pair_index in range(arguments.pairs):
        order = (
            [OLD_VERSION, NEW_VERSION]
            if pair_index % 2 == 0
            else [NEW_VERSION, OLD_VERSION]
        )
        pair = {"index": pair_index + 1, "order": order, "results": {}}
        for version in order:
            print(f"Pair {pair_index + 1}/{arguments.pairs}: Jetty {version}", flush=True)
            result_name = f"pair-{pair_index + 1:02d}-{version}"
            pair["results"][version] = run_trial(version, result_name)
        pairs.append(pair)
        write_results(arguments.output, pairs)

    server_summary = summarize(pairs, "serverAllocatedBytes")
    lower, upper = server_summary["differenceConfidenceInterval95"]
    print(
        f"Server allocation difference: {server_summary['differenceMean']:+.0f} B/op "
        f"(95% CI {lower:+.0f} to {upper:+.0f} B/op)"
    )
    print(f"Results: {arguments.output}")


if __name__ == "__main__":
    main()
