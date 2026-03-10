"""
Show added/removed allocation call sites for a specific source file.

Modes:
  python check_sites.py OLD_COMMIT[..NEW_COMMIT] SCENARIO SOURCE_FILE
      Compare _sites.json from two git commits.
      NEW_COMMIT defaults to HEAD if omitted.

  python check_sites.py --local SCENARIO SOURCE_FILE
      Compare local build/allocations/ against local allocations/.

SCENARIO: path to _sites.json relative to the allocations root, without extension
          e.g. helloWorld[CIO]  or  client/streamingResponse[CIO]
SOURCE_FILE: source file name to inspect, e.g. ByteChannel.kt

Run from the ktor-benchmarks repository root.
"""

import sys

from sources import git_sources, local_sources


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
    mode = "--local"
else:
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    old_source, new_source = git_sources(sys.argv[1])
    scenario = sys.argv[2]
    source_file = sys.argv[3]
    mode = f"{old_source.ref}..{new_source.ref}"


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


def format_frames(stack_trace):
    """Format up to 4 frames from a raw stackTrace string, collapsing consecutive
    frames from the same file into a single token with multiple line numbers.

    Frame separator is ' <- ' (call direction: allocating site on the left).
    Consecutive same-file frames are collapsed: 'File.kt:21:52' means lines 21
    and 52 of File.kt appeared as adjacent frames.
    """
    frames = stack_trace.split(", ")[:4]
    result = []
    i = 0
    while i < len(frames):
        file = frames[i].split(":")[0]
        lines = []
        while i < len(frames) and frames[i].split(":")[0] == file:
            parts = frames[i].split(":")
            if len(parts) > 1:
                lines.append(parts[1])
            i += 1
        token = file + (":" + ":".join(lines) if lines else "")
        result.append(token)
    return " <- ".join(result)


def stable_key(stack_trace):
    """
    Normalize caller line numbers so traces differing only in caller shifts match.

    Keeps the top (allocating) frame line number intact so two distinct call sites
    in the inspected file are never merged. Strips line numbers only from caller
    frames, which loses the exact caller line in CHANGED output but avoids spurious
    ADDED/REMOVED pairs when unrelated Ktor code insertions shift caller lines.

    Output format: 'file:line' for frame 0, bare 'file' for frames 1+.
    Used only for grouping — display uses the original stackTrace.
    """
    frames = stack_trace.split(", ")
    normalized = [frames[0]] + [f.split(":")[0] for f in frames[1:]]
    return ", ".join(normalized)


def build_map(sites):
    """Build {stable_key: site} merging entries that share the same stable stack trace.

    When two raw entries share a stable key (caller line numbers differed), their
    totalSize values are summed. All other fields (name, stackTrace, …) are kept
    from the first occurrence. This is safe because stable_key preserves the top
    frame's line number, so two sites that allocate *different types* at *different
    lines* in the inspected file always produce distinct keys and are never merged.
    """
    result = {}
    for s in sites:
        key = stable_key(s["stackTrace"])
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

if not added and not removed and not changed:
    sys.exit(0)

print(f"\n{scenario} / {source_file}  {mode}")

entries = []

for _, s in added.items():
    n = s["totalSize"]
    entries.append((n, f"  +{n:,}  [{s['name']}]  {format_frames(s['stackTrace'])}"))

for _, s in removed.items():
    o = s["totalSize"]
    entries.append((-o, f"  -{o:,}  [{s['name']}]  {format_frames(s['stackTrace'])}"))

for _, (old_s, new_s) in changed.items():
    delta = new_s["totalSize"] - old_s["totalSize"]
    entries.append((delta, f"  {delta:+,} ({old_s['totalSize']:,} → {new_s['totalSize']:,})  [{new_s['name']}]  {format_frames(new_s['stackTrace'])}"))

for _, line in sorted(entries, key=lambda x: (int(x[0] < 0), -abs(x[0]))):
    print(line)
