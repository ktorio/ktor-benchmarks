---
name: allocations
description: Use when working with Ktor memory allocation benchmarks — showing how allocations changed between versions, updating the committed baseline after PRs are merged, or preparing a release report. Also use when the user mentions allocation diffs, dump files, a TeamCity build URL for allocation tests, PR numbers that might affect allocations, or asks to "release" results for a new Ktor version.
---

## Overview

Manages Ktor allocation benchmark data: diffing two versions, updating the committed baseline after PRs merge, and finalizing release reports. Diff analysis honors documented bounds in `allocation-benchmark/allocations/tolerances.json` while always reporting raw changes.

## Commands

| Command | When to use |
|---------|-------------|
| `diff [OLD] [NEW]` | Print full per-file allocation diffs between two versions |
| `update [hints...]` | Get fresh dumps, verify against known PRs/tasks, commit updated baseline |
| `release [OLD] [NEW]` | Finalize pending.md into a versioned release report and bump the version |

## Dispatch

Infer the command from `$ARGUMENTS` and the surrounding conversation:

- Two version numbers present + "diff" / "show diffs" / "show allocations" intent → **`diff`**, use them as OLD and NEW
- Two version numbers present with report/publish intent → **`release`**, use them as OLD and NEW
- "update", "baseline", "dump", PR numbers, or YouTrack task IDs present → **`update`**, treat any PR/task refs as hints
- Genuinely ambiguous → ask the user

**`release`** — Read and follow `@references/release.md`.

**`update`** — Read and follow `@references/update.md`.

**`diff`** — Run from the ktor-benchmarks root and print the output:
```bash
# Same main baseline family across benchmark commits
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py --baseline main OLD_COMMIT..NEW_COMMIT

# Release tags infer release/MAJOR.x automatically
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py vOLD..vNEW

# Explicit cross-family comparison
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py \
  --old-baseline release/MAJOR.x --new-baseline main OLD_COMMIT..NEW_COMMIT
```
Version tags follow the `vX.Y.Z` convention. Revisions created before branch-specific baseline directories use the legacy root layout automatically. If two refs imply different or unknown baseline families, select one family with `--baseline` or both sides with `--old-baseline` and `--new-baseline`.

---

## Prerequisites

Resolve each item below **at the point it is first needed** — not upfront. Each reference file will say "resolve X — see Prerequisites in SKILL.md" when it requires something here.

**Ktor repo path**
Check memory for a saved ktor repo path. If found, verify it is still valid (`ls KTOR_REPO_PATH/VERSION`). If missing or invalid, ask the user where the ktor repo is cloned (offer to clone to `/tmp/ktor` if needed). Save the resolved path to memory.

**TC CLI**
Run `teamcity auth status`. If not found or not authenticated, use the `teamcity-cli:teamcity-cli` skill for setup before proceeding.

**gh CLI**
Run `gh auth status`. If not authenticated, run `gh auth login` before proceeding.

**YouTrack MCP**
Call `search_issues` with query `project: KTOR #Resolved` limit 1. If unavailable, note it — the step that needs it will skip the YouTrack sub-step and rely on `git log` + `gh` only.
