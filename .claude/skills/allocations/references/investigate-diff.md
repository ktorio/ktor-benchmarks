# Investigating allocation diffs

## Reading the diff output

From `compute_diff.py` output, identify:

**Consistent changes** — files that moved by a similar amount across *multiple* engines/scenarios point to a shared cause.

**Large outliers** — a single-file change that is 10× larger than the rest deserves individual explanation.

**Correlated files** — files that always move together probably share a root cause; group them.

## Quick investigation — inspecting call sites

For any change that is unclear, use `check_sites.py` to see added/removed/changed stack traces for a specific source file. Run from the ktor-benchmarks root.

The `SCENARIO[ENGINE]` argument is the path relative to the allocations root without the `_sites.json` suffix — e.g. `helloWorld[CIO]` or `client/streamingResponse[CIO]`. The square brackets and engine name are literal.

**Local mode** (new dumps in `build/allocations/`):
```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/check_sites.py --local \
  "SCENARIO[ENGINE]" \
  FileName.kt
```

**Git mode** (comparing two commits). For a release use `vOLD..vNEW`; for an in-progress update where the new tag doesn't exist yet, omit `..vNEW` — it defaults to `HEAD`:
```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/check_sites.py vOLD_VERSION[..vNEW_VERSION] \
  "SCENARIO[ENGINE]" \
  FileName.kt
```

The class names and stack frames in the output are authoritative: they confirm exactly what is being allocated and from which call path.

## Deep investigation — attributing changes to PRs

Use this when no PR hints were provided, or to confirm an attribution that is still unclear after inspecting call sites.

Resolve **Ktor repo path** — see Prerequisites in SKILL.md.

The caller must supply the git commit range (e.g. `vOLD..vNEW` for a release, `v{PREV_VERSION}..HEAD` for an in-progress update).

**a) For each file showing a significant change, find which commit in the range touched it:**
```bash
git log --oneline RANGE -- "**/TheFile.kt"
```

**b) Get PR details for relevant commits** — resolve **gh CLI** (see Prerequisites in SKILL.md):
```bash
gh pr view PR_NUMBER --repo ktorio/ktor --json title,body
```

**c) Extract YouTrack issue ID** — check the PR title and body (from step b) for a `KTOR-XXXX` reference. This is the fastest path and usually sufficient.

If no issue ID is found in the PR, fall back to **YouTrack MCP** — resolve **YouTrack MCP** (see Prerequisites in SKILL.md); skip if unavailable. Search for issues resolved in the relevant release:
```text
project: KTOR {Target release}: VERSION #Resolved
```
Cross-reference the YouTrack issues with the commits and PRs to confirm attribution.

Attribution format in reports: `[KTOR-XXXX / #YYYY](PR_URL)` — both the YouTrack issue ID and the PR number in the link text, linking to the PR URL. Omit the YouTrack part if no issue is associated.

If no commit or PR can be found for a significant change, write "cause under investigation" — don't omit the change.
