"""Shared allocation call-site analysis."""


def top_frame(site):
    return site["stackTrace"].split(", ")[0].split(":")[0]


def group_by_file(sites):
    grouped = {}
    for site in sites:
        grouped.setdefault(top_frame(site), []).append(site)
    return grouped


def format_frames(stack_trace):
    """Format up to four frames, collapsing consecutive frames from the same file."""
    frames = stack_trace.split(", ")[:4]
    result = []
    index = 0
    while index < len(frames):
        source_file = frames[index].split(":")[0]
        lines = []
        while index < len(frames) and frames[index].split(":")[0] == source_file:
            parts = frames[index].split(":")
            if len(parts) > 1:
                lines.append(parts[1])
            index += 1
        token = source_file + (":" + ":".join(lines) if lines else "")
        result.append(token)
    return " <- ".join(result)


def stable_key(stack_trace):
    """Normalize caller line numbers while preserving the allocating frame line."""
    frames = stack_trace.split(", ")
    normalized = [frames[0]] + [frame.split(":")[0] for frame in frames[1:]]
    return ", ".join(normalized)


def build_map(sites):
    """Build {(allocation type, stable stack trace): aggregated site}."""
    result = {}
    for site in sites:
        key = (site["name"], stable_key(site["stackTrace"]))
        if key in result:
            result[key] = dict(
                result[key],
                totalSize=result[key]["totalSize"] + site["totalSize"],
            )
        else:
            result[key] = site
    return result


def analyze_grouped_sites(old_by_file, new_by_file, source_file):
    if source_file not in old_by_file and source_file not in new_by_file:
        known_files = sorted(set(old_by_file) | set(new_by_file))[:20]
        raise ValueError(
            f"{source_file!r} not found in either snapshot — check spelling. "
            f"Known files: {', '.join(known_files)}"
        )

    old_map = build_map(old_by_file.get(source_file, []))
    new_map = build_map(new_by_file.get(source_file, []))
    changes = []

    for key, site in new_map.items():
        if key not in old_map:
            changes.append(
                {
                    "kind": "added",
                    "allocationType": site["name"],
                    "oldSize": 0,
                    "newSize": site["totalSize"],
                    "rawDelta": site["totalSize"],
                    "oldStackTrace": None,
                    "newStackTrace": site["stackTrace"],
                }
            )

    for key, site in old_map.items():
        if key not in new_map:
            changes.append(
                {
                    "kind": "removed",
                    "allocationType": site["name"],
                    "oldSize": site["totalSize"],
                    "newSize": 0,
                    "rawDelta": -site["totalSize"],
                    "oldStackTrace": site["stackTrace"],
                    "newStackTrace": None,
                }
            )

    for key, old_site in old_map.items():
        if key not in new_map or old_site["totalSize"] == new_map[key]["totalSize"]:
            continue
        new_site = new_map[key]
        changes.append(
            {
                "kind": "changed",
                "allocationType": new_site["name"],
                "oldSize": old_site["totalSize"],
                "newSize": new_site["totalSize"],
                "rawDelta": new_site["totalSize"] - old_site["totalSize"],
                "oldStackTrace": old_site["stackTrace"],
                "newStackTrace": new_site["stackTrace"],
            }
        )

    changes.sort(key=lambda change: (int(change["rawDelta"] < 0), -abs(change["rawDelta"])))
    return changes
