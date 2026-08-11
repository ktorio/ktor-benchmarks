"""Shared allocation data sources for compute_diff.py and check_sites.py."""

import json
import math
import os
import re
import subprocess

REPO = os.getcwd()
ALLOC_GIT_ROOT = "allocation-benchmark/allocations"
ALLOC_LOCAL_ROOT = "allocation-benchmark/build/allocations"
RELEASE_BASELINE_PATTERN = re.compile(r"^release-(\d+)\.x$")
RELEASE_BRANCH_PATTERN = re.compile(r"^(?:refs/heads/)?release/(\d+)\.x$")
TOLERANCES_FILE = "tolerances.json"
LFS_POINTER_HEADER = b"version https://git-lfs.github.com/spec/v1\n"


def parse_baseline_arguments(args):
    args = list(args)
    args, baseline = extract_option(args, "--baseline")
    args, old_baseline = extract_option(args, "--old-baseline")
    args, new_baseline = extract_option(args, "--new-baseline")

    if baseline and (old_baseline or new_baseline):
        raise ValueError(
            "--baseline cannot be combined with --old-baseline or --new-baseline"
        )
    if bool(old_baseline) != bool(new_baseline):
        raise ValueError(
            "--old-baseline and --new-baseline must be specified together"
        )

    if baseline:
        old_baseline = new_baseline = baseline

    return (
        args,
        normalize_baseline(old_baseline) if old_baseline else None,
        normalize_baseline(new_baseline) if new_baseline else None,
    )


def extract_option(args, name):
    if args.count(name) > 1:
        raise ValueError(f"{name} may be specified only once")
    if name not in args:
        return args, None

    index = args.index(name)
    try:
        value = args[index + 1]
    except IndexError:
        raise ValueError(f"{name} requires a baseline name") from None
    if value.startswith("--"):
        raise ValueError(f"{name} requires a baseline name")
    return args[:index] + args[index + 2:], value


def release_allocation_baseline(major_version):
    return f"release-{major_version}.x"


def normalize_baseline(baseline):
    if baseline in {"main", "refs/heads/main"}:
        return "main"
    if match := RELEASE_BRANCH_PATTERN.match(baseline):
        return release_allocation_baseline(match.group(1))
    if RELEASE_BASELINE_PATTERN.match(baseline):
        return baseline
    raise ValueError(
        f"unsupported baseline {baseline!r}; expected 'main' or 'release/MAJOR.x'"
    )


def infer_local_baseline():
    versions_file = os.path.join(REPO, "libs.versions.toml")
    try:
        with open(versions_file) as file:
            content = file.read()
    except OSError:
        raise ValueError("cannot read libs.versions.toml; specify --baseline") from None

    match = re.search(r'^ktor\s*=\s*"(\d+)\.(\d+)\.(\d+)', content, re.MULTILINE)
    if match and int(match.group(3)) != 0:
        return release_allocation_baseline(match.group(1))

    version = ".".join(match.groups()) if match else "unknown"
    raise ValueError(
        f"cannot infer a baseline from Ktor version {version!r}; "
        "specify --baseline main or --baseline release/MAJOR.x"
    )


def validate_tolerances(metadata):
    if not isinstance(metadata, dict):
        raise ValueError("Tolerance metadata must be a JSON object")

    allowed_root_keys = {"defaultAllowedIncreaseRatio", "reports"}
    unknown_root_keys = set(metadata) - allowed_root_keys
    if unknown_root_keys:
        raise ValueError(f"Unknown tolerance metadata keys: {sorted(unknown_root_keys)}")

    ratio = metadata.get("defaultAllowedIncreaseRatio")
    if (
        isinstance(ratio, bool)
        or not isinstance(ratio, (int, float))
        or not math.isfinite(ratio)
        or ratio < 0
    ):
        raise ValueError("defaultAllowedIncreaseRatio must be a non-negative number")

    reports = metadata.get("reports", {})
    if not isinstance(reports, dict):
        raise ValueError("reports must be a JSON object")
    for report_name, report in reports.items():
        if not isinstance(report, dict) or set(report) - {"locations"}:
            raise ValueError(f"Invalid tolerance metadata for report {report_name!r}")
        locations = report.get("locations", {})
        if not isinstance(locations, dict):
            raise ValueError(f"locations must be a JSON object for report {report_name!r}")
        for source_file, variance in locations.items():
            if not isinstance(variance, dict):
                raise ValueError(f"Invalid known variance for {report_name!r} / {source_file!r}")
            unknown_variance_keys = set(variance) - {"knownVarianceBytes", "reason", "reference"}
            if unknown_variance_keys:
                raise ValueError(
                    f"Unknown known-variance keys for {report_name!r} / {source_file!r}: "
                    f"{sorted(unknown_variance_keys)}"
                )
            variance_bytes = variance.get("knownVarianceBytes")
            if isinstance(variance_bytes, bool) or not isinstance(variance_bytes, int) or variance_bytes < 0:
                raise ValueError(
                    f"knownVarianceBytes must be a non-negative integer for "
                    f"{report_name!r} / {source_file!r}"
                )
            reason = variance.get("reason")
            if not isinstance(reason, str) or not reason.strip():
                raise ValueError(f"reason must not be blank for {report_name!r} / {source_file!r}")
            reference = variance.get("reference")
            if reference is not None and not isinstance(reference, str):
                raise ValueError(f"reference must be a string for {report_name!r} / {source_file!r}")

    return metadata


def known_location_variance(metadata, report_name, source_file):
    return (
        metadata.get("reports", {})
        .get(report_name, {})
        .get("locations", {})
        .get(source_file)
    )


class GitSource:
    def __init__(self, ref, baseline):
        try:
            subprocess.check_output(
                ["git", "rev-parse", "--verify", ref], cwd=REPO, stderr=subprocess.DEVNULL
            )
        except subprocess.CalledProcessError:
            raise ValueError(f"Invalid git ref: {ref!r}") from None
        self.ref = ref
        self.baseline = baseline

        baseline_directories = self._baseline_directories()
        if baseline in baseline_directories:
            self.data_root = f"{ALLOC_GIT_ROOT}/{baseline}"
        elif baseline_directories:
            available = ", ".join(sorted(baseline_directories))
            raise FileNotFoundError(
                f"Allocation baseline {baseline!r} not found at {ref!r}. "
                f"Available baselines: {available}"
            )
        else:
            self.data_root = ALLOC_GIT_ROOT

    def _baseline_directories(self):
        try:
            output = subprocess.check_output(
                ["git", "ls-tree", "--name-only", f"{self.ref}:{ALLOC_GIT_ROOT}"],
                cwd=REPO,
                stderr=subprocess.DEVNULL,
                text=True,
            )
        except subprocess.CalledProcessError:
            raise FileNotFoundError(
                f"Allocation data not found at {self.ref!r}"
            ) from None
        return {
            name
            for name in output.splitlines()
            if name == "main" or RELEASE_BASELINE_PATTERN.fullmatch(name)
        }

    def list_files(self, subdir):
        git_dir = f"{self.data_root}/{subdir}".rstrip("/")
        try:
            out = subprocess.check_output(
                ["git", "ls-tree", "--name-only", f"{self.ref}:{git_dir}"], cwd=REPO
            ).decode()
        except subprocess.CalledProcessError:
            return []
        return [f for f in out.splitlines() if f.endswith(".json") and "_sites" not in f]

    def _exists(self, path):
        output = subprocess.check_output(
            ["git", "ls-tree", "--name-only", self.ref, "--", path],
            cwd=REPO,
            stderr=subprocess.DEVNULL,
        )
        return bool(output.strip())

    def _show(self, path):
        """Return file content at this ref, resolving LFS pointers if needed."""
        raw = subprocess.check_output(
            ["git", "show", f"{self.ref}:{path}"], cwd=REPO
        )
        if not raw.startswith(LFS_POINTER_HEADER):
            return raw
        return subprocess.check_output(["git", "lfs", "smudge"], input=raw, cwd=REPO)

    def load(self, subdir, fname):
        git_dir = f"{self.data_root}/{subdir}".rstrip("/")
        return json.loads(self._show(f"{git_dir}/{fname}"))

    def load_tolerances(self):
        path = f"{ALLOC_GIT_ROOT}/{TOLERANCES_FILE}"
        if not self._exists(path):
            return {}
        return validate_tolerances(json.loads(self._show(path)))

    def load_sites(self, scenario, required=True):
        path = f"{self.data_root}/{scenario}_sites.json"
        try:
            raw = self._show(path)
        except subprocess.CalledProcessError:
            if required:
                raise FileNotFoundError(f"{path} not found at {self.ref!r}") from None
            return []
        if not raw.strip():
            if required:
                raise FileNotFoundError(f"{path} not found at {self.ref!r}")
            return []
        return json.loads(raw)


class LocalSource:
    def __init__(self, root=ALLOC_LOCAL_ROOT, tolerances_root=None):
        if not os.path.isdir(root):
            raise FileNotFoundError(f"Directory not found: {root}")
        self.root = root
        self.tolerances_root = tolerances_root or root

    def list_files(self, subdir):
        local_dir = os.path.join(self.root, subdir) if subdir else self.root
        if not os.path.isdir(local_dir):
            return []
        return [f for f in os.listdir(local_dir) if f.endswith(".json") and "_sites" not in f]

    def load(self, subdir, fname):
        local_dir = os.path.join(self.root, subdir) if subdir else self.root
        with open(os.path.join(local_dir, fname)) as f:
            return json.load(f)

    def load_tolerances(self):
        path = os.path.join(self.tolerances_root, TOLERANCES_FILE)
        if not os.path.exists(path):
            return {}
        with open(path) as f:
            return validate_tolerances(json.load(f))

    def load_sites(self, scenario, required=True):
        path = os.path.join(self.root, f"{scenario}_sites.json")
        if not os.path.exists(path):
            if required:
                raise FileNotFoundError(f"{path} not found")
            return []
        with open(path) as f:
            return json.load(f)


def git_sources(ref, old_baseline=None, new_baseline=None):
    """Parse 'OLD[..NEW]' and return (GitSource, GitSource), defaulting NEW to HEAD."""
    if ".." in ref:
        old, new = ref.split("..", 1)
    else:
        old, new = ref, "HEAD"

    if (old_baseline is None) != (new_baseline is None):
        raise ValueError("old_baseline and new_baseline must be specified together")

    if old_baseline is None:
        old_baseline = infer_git_baseline(old)
        new_baseline = infer_git_baseline(new)
        if old_baseline is None or new_baseline is None:
            raise ValueError(
                f"cannot infer allocation baselines for {ref!r}. "
                "Use --baseline to compare one family, or specify both "
                "--old-baseline and --new-baseline."
            )
        if old_baseline != new_baseline:
            raise ValueError(
                f"{ref!r} selects different baseline families: "
                f"{old_baseline!r} and {new_baseline!r}. "
                "Use --baseline to compare one family, or specify both "
                "--old-baseline and --new-baseline for a cross-family comparison."
            )

    return GitSource(old, old_baseline), GitSource(new, new_baseline)


def infer_git_baseline(ref):
    ref_name = ref.removeprefix("refs/heads/").removeprefix("refs/remotes/")
    ref_name = ref_name.removeprefix("origin/")
    ref_name = re.split(r"[~^]", ref_name, maxsplit=1)[0]

    if ref_name == "HEAD":
        try:
            branch = subprocess.check_output(
                ["git", "symbolic-ref", "--quiet", "--short", "HEAD"],
                cwd=REPO,
                stderr=subprocess.DEVNULL,
                text=True,
            ).strip()
        except subprocess.CalledProcessError:
            branch = ""
        if branch and branch != "HEAD":
            return infer_git_baseline(branch)

    if ref_name == "main":
        return "main"
    if match := re.fullmatch(r"release/(\d+)\.x", ref_name):
        return release_allocation_baseline(match.group(1))
    if match := re.fullmatch(r"v(\d+)\.\d+\.\d+(?:[-+].*)?", ref_name):
        return release_allocation_baseline(match.group(1))

    try:
        tags = subprocess.check_output(
            ["git", "tag", "--points-at", ref],
            cwd=REPO,
            stderr=subprocess.DEVNULL,
            text=True,
        ).splitlines()
    except subprocess.CalledProcessError:
        return None
    baselines = {
        release_allocation_baseline(match.group(1))
        for tag in tags
        if (match := re.fullmatch(r"v(\d+)\.\d+\.\d+(?:[-+].*)?", tag))
    }
    return baselines.pop() if len(baselines) == 1 else None


def local_sources(baseline=None):
    """Return (old, new) as LocalSource instances for local mode.

    old: allocation-benchmark/allocations/BASELINE/ (committed baseline)
    new: allocation-benchmark/build/allocations/ (freshly generated dumps)
    """
    baseline = baseline or infer_local_baseline()
    tolerances_root = os.path.join(REPO, ALLOC_GIT_ROOT)
    return (
        LocalSource(
            root=os.path.join(tolerances_root, baseline),
            tolerances_root=tolerances_root,
        ),
        LocalSource(),
    )
