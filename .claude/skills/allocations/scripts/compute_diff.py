"""
Compute per-file allocation diffs across all discovered scenarios.

Modes:
  python compute_diff.py OLD_COMMIT[..NEW_COMMIT]
      Compare allocation dumps from two git commits.
      NEW_COMMIT defaults to HEAD if omitted.

  python compute_diff.py --local
      Compare local build/allocations/ against local allocations/.

Options:
  --threshold N   Hide per-file entries with |delta| < N bytes (default: 50).
                  Use --threshold 0 to show all entries.
                  Affects only changed sites, added and removed sites are always shown.

Run from the ktor-benchmarks repository root.
"""

import re
import subprocess
import sys

from sources import git_sources, local_sources

SUBDIRS = {"server": "", "client": "client"}


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

args = sys.argv[1:]

threshold = 50
if "--threshold" in args:
    i = args.index("--threshold")
    try:
        threshold = int(args[i + 1])
    except (IndexError, ValueError):
        print("ERROR: --threshold requires a non-negative integer argument", file=sys.stderr)
        sys.exit(1)
    if threshold < 0:
        print("ERROR: --threshold must be non-negative", file=sys.stderr)
        sys.exit(1)
    args = args[:i] + args[i + 2:]

if args and args[0] == "--local":
    old_source, new_source = local_sources()
    mode = "--local"
else:
    if not args:
        print(__doc__)
        sys.exit(1)
    old_source, new_source = git_sources(args[0])
    mode = f"{old_source.ref}..{new_source.ref}"

print(f"{mode}  --threshold={threshold}")


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
        diffs.sort(key=lambda x: (int(x[0] < 0), -abs(x[0])))
        results.setdefault(key, {})[engine] = {
            "old_total": old_total,
            "new_total": new_total,
            "delta": new_total - old_total,
            "diffs": diffs,  # list of (delta, filename, old, new)
        }

for scenario, engines in results.items():
    print(f"\n{scenario}")
    for engine, data in engines.items():
        delta = data["delta"]
        old_kb = data["old_total"] / 1024
        new_kb = data["new_total"] / 1024
        print(f"  {engine}: {old_kb:.2f} KB → {new_kb:.2f} KB  ({delta:+,})")
        hidden_count = 0
        hidden_sum = 0
        for d, fname, o, n in data["diffs"]:
            if o == 0:
                print(f"    +{n:,}  {fname}")
            elif n == 0:
                print(f"    -{o:,}  {fname}")
            elif abs(d) < threshold:
                hidden_count += 1
                hidden_sum += d
            else:
                print(f"    {d:+,} ({o:,} → {n:,})  {fname}")
        if hidden_count:
            print(f"    ({hidden_count} below threshold, net {hidden_sum:+,})")
