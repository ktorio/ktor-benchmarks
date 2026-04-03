"""Shared allocation data sources for compute_diff.py and check_sites.py."""

import json
import os
import subprocess

REPO = os.getcwd()
ALLOC_GIT_ROOT = "allocation-benchmark/allocations"
ALLOC_LOCAL_ROOT = "allocation-benchmark/build/allocations"


class GitSource:
    def __init__(self, ref):
        try:
            subprocess.check_output(
                ["git", "rev-parse", "--verify", ref], cwd=REPO, stderr=subprocess.DEVNULL
            )
        except subprocess.CalledProcessError:
            raise ValueError(f"Invalid git ref: {ref!r}") from None
        self.ref = ref

    def list_files(self, subdir):
        git_dir = f"{ALLOC_GIT_ROOT}/{subdir}".rstrip("/")
        try:
            out = subprocess.check_output(
                ["git", "ls-tree", "--name-only", f"{self.ref}:{git_dir}"], cwd=REPO
            ).decode()
        except subprocess.CalledProcessError:
            return []
        return [f for f in out.splitlines() if f.endswith(".json") and "_sites" not in f]

    def _show(self, path):
        """Return file content at this ref, resolving LFS pointers if needed."""
        raw = subprocess.check_output(
            ["git", "show", f"{self.ref}:{path}"], cwd=REPO
        )
        return subprocess.check_output(["git", "lfs", "smudge"], input=raw, cwd=REPO)

    def load(self, subdir, fname):
        git_dir = f"{ALLOC_GIT_ROOT}/{subdir}".rstrip("/")
        return json.loads(self._show(f"{git_dir}/{fname}"))

    def load_sites(self, scenario, required=True):
        path = f"{ALLOC_GIT_ROOT}/{scenario}_sites.json"
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
    def __init__(self, root=ALLOC_LOCAL_ROOT):
        if not os.path.isdir(root):
            raise FileNotFoundError(f"Directory not found: {root}")
        self.root = root

    def list_files(self, subdir):
        local_dir = os.path.join(self.root, subdir) if subdir else self.root
        if not os.path.isdir(local_dir):
            return []
        return [f for f in os.listdir(local_dir) if f.endswith(".json") and "_sites" not in f]

    def load(self, subdir, fname):
        local_dir = os.path.join(self.root, subdir) if subdir else self.root
        with open(os.path.join(local_dir, fname)) as f:
            return json.load(f)

    def load_sites(self, scenario, required=True):
        path = os.path.join(self.root, f"{scenario}_sites.json")
        if not os.path.exists(path):
            if required:
                raise FileNotFoundError(f"{path} not found")
            return []
        with open(path) as f:
            return json.load(f)


def git_sources(ref):
    """Parse 'OLD[..NEW]' and return (GitSource, GitSource), defaulting NEW to HEAD."""
    if ".." in ref:
        old, new = ref.split("..", 1)
    else:
        old, new = ref, "HEAD"
    return GitSource(old), GitSource(new)


def local_sources():
    """Return (old, new) as LocalSource instances for local mode.

    old: allocation-benchmark/allocations/ (committed baseline)
    new: allocation-benchmark/build/allocations/ (freshly generated dumps)
    """
    return LocalSource(root=os.path.join(REPO, ALLOC_GIT_ROOT)), LocalSource()
