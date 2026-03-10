"""
Show added/removed allocation call sites for a specific source file.

Modes:
  python check_sites.py OLD_COMMIT SCENARIO SOURCE_FILE [NEW_COMMIT]
      Compare _sites.json from two git commits.
      NEW_COMMIT defaults to HEAD.

  python check_sites.py --local SCENARIO SOURCE_FILE
      Compare local build/allocations/ against local allocations/.

SCENARIO: path to _sites.json relative to the allocations root, without extension
          e.g. helloWorld[CIO]  or  client/streamingResponse[CIO]
SOURCE_FILE: source file name to inspect, e.g. ByteChannel.kt

Run from the ktor-benchmarks repository root.
"""

import sys

from sources import GitSource, local_sources, ALLOC_LOCAL_ROOT, ALLOC_GIT_ROOT


# ---------------------------------------------------------------------------
# Mode selection
# ---------------------------------------------------------------------------

if len(sys.argv) >= 2 and sys.argv[1] == "--local":
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    scenario = sys.argv[2]
    source_file = sys.argv[3]
    old_source, new_source = local_sources()
    print(f"Mode: local  ({ALLOC_GIT_ROOT} → {ALLOC_LOCAL_ROOT})")
else:
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    old_commit = sys.argv[1]
    scenario = sys.argv[2]
    source_file = sys.argv[3]
    new_commit = sys.argv[4] if len(sys.argv) > 4 else "HEAD"
    old_source = GitSource(old_commit)
    new_source = GitSource(new_commit)
    print(f"Mode: commits  ({old_commit} → {new_commit})")


# ---------------------------------------------------------------------------
# Analysis
# ---------------------------------------------------------------------------

def top_frame(s):
    return s["stackTrace"].split(", ")[0].split(":")[0]


def group_by_file(sites):
    by_file = {}
    for s in sites:
        by_file.setdefault(top_frame(s), []).append(s)
    return by_file


def build_map(sites):
    """Build {stack_trace: site} merging entries that share the same stack trace."""
    result = {}
    for s in sites:
        key = s["stackTrace"]
        if key in result:
            result[key] = dict(result[key], totalSize=result[key]["totalSize"] + s["totalSize"])
        else:
            result[key] = s
    return result


try:
    new_sites = new_source.load_sites(scenario, required=True)
except FileNotFoundError as e:
    print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)
old_sites = old_source.load_sites(scenario, required=False)

old_by_file = group_by_file(old_sites)
new_by_file = group_by_file(new_sites)

if source_file not in old_by_file and source_file not in new_by_file:
    print(f"ERROR: '{source_file}' not found in either snapshot — check spelling", file=sys.stderr)
    print(f"Known files: {', '.join(sorted(set(old_by_file) | set(new_by_file))[:20])}", file=sys.stderr)
    sys.exit(1)

o_map = build_map(old_by_file.get(source_file, []))
n_map = build_map(new_by_file.get(source_file, []))

added = {k: v for k, v in n_map.items() if k not in o_map}
removed = {k: v for k, v in o_map.items() if k not in n_map}
changed = {
    k: (o_map[k], n_map[k])
    for k in o_map
    if k in n_map and o_map[k]["totalSize"] != n_map[k]["totalSize"]
}

print(f"\n=== {source_file} in {scenario} ===\n")

if added:
    print("ADDED:")
    for st, s in sorted(added.items(), key=lambda x: -x[1]["totalSize"]):
        frames = ", ".join(st.split(", ")[:4])
        print(f"  +{s['totalSize']:,} bytes  [{s['name']}]  {frames}")

if removed:
    print("\nREMOVED:")
    for st, s in sorted(removed.items(), key=lambda x: -x[1]["totalSize"]):
        frames = ", ".join(st.split(", ")[:4])
        print(f"  -{s['totalSize']:,} bytes  [{s['name']}]  {frames}")

if changed:
    print("\nCHANGED:")
    for st, (old_s, new_s) in sorted(changed.items(), key=lambda x: -(x[1][1]["totalSize"] - x[1][0]["totalSize"])):
        delta = new_s["totalSize"] - old_s["totalSize"]
        frames = ", ".join(st.split(", ")[:4])
        print(f"  {delta:+,} bytes  [{new_s['name']}]  {frames}")

if not added and not removed and not changed:
    print("No allocation changes for this file.")
