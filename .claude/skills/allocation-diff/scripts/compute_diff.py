"""
Compute per-file allocation diffs across all discovered scenarios.

Modes:
  python compute_diff.py OLD_COMMIT [NEW_COMMIT]
      Compare allocation dumps from two git commits.
      NEW_COMMIT defaults to HEAD.

  python compute_diff.py --local
      Compare local build/allocations/ against local allocations/.

Run from the ktor-benchmarks repository root.
"""

import json
import re
import subprocess
import sys

from sources import GitSource, local_sources, ALLOC_LOCAL_ROOT, ALLOC_GIT_ROOT

SUBDIRS = {"server": "", "client": "client"}


# ---------------------------------------------------------------------------
# Mode selection
# ---------------------------------------------------------------------------

if len(sys.argv) >= 2 and sys.argv[1] == "--local":
    old_source, new_source = local_sources()
    print(f"Mode: local  ({ALLOC_GIT_ROOT} → {ALLOC_LOCAL_ROOT})")
else:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    old_commit = sys.argv[1]
    new_commit = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
    old_source = GitSource(old_commit)
    new_source = GitSource(new_commit)
    print(f"Mode: commits  ({old_commit} → {new_commit})")


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
        diffs.sort()
        results.setdefault(key, {})[engine] = {
            "old_total": old_total,
            "new_total": new_total,
            "delta": new_total - old_total,
            "diffs": diffs,  # list of (delta, filename, old, new)
        }

for scenario, engines in results.items():
    print(f"\n{'='*60}")
    print(f"SCENARIO: {scenario}")
    for engine, data in engines.items():
        delta = data["delta"]
        old_kb = data["old_total"] / 1024
        new_kb = data["new_total"] / 1024
        print(f"  {engine}: {old_kb:.2f} KB → {new_kb:.2f} KB  ({delta:+,} bytes)")
        for d, fname, o, n in data["diffs"]:
            print(f"    {d:+,}  {fname}  ({o} → {n})")
