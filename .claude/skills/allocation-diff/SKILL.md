---
name: allocation-diff
description: Generate an allocation benchmark comparison report between two Ktor versions. Use when the user asks to generate an allocation report or diff after updating allocation dumps.
argument-hint: [OLD_VERSION NEW_VERSION]
---

Produce a human-readable markdown report comparing allocation benchmark results between two Ktor versions. Follow every step below in order.

If $ARGUMENTS is empty, ask the user for OLD_VERSION and NEW_VERSION before proceeding.

---

## Step 1 — Establish baseline commits

This skill is expected to be run from a branch that contains a commit updating the allocation dumps for NEW_VERSION. The workflow is:

- **new state** = `HEAD` (the tip of the current branch, which includes the updated dumps)
- **old state** = the parent of the first commit on this branch that touched `allocation-benchmark/allocations/`

Find that commit (scoped to branch-local commits only):

```bash
MERGE_BASE=$(git merge-base HEAD origin/main)
git log --oneline "${MERGE_BASE}..HEAD" -- allocation-benchmark/allocations/
```

The branch may contain other commits, but there will be exactly one touching the allocation dumps. Its parent is the old baseline:

```bash
MERGE_BASE=$(git merge-base HEAD origin/main)
OLD_COMMIT=$(git log --oneline "${MERGE_BASE}..HEAD" -- allocation-benchmark/allocations/ | awk '{print $1}')^
NEW_COMMIT=HEAD
```

**If no such commit exists** (the branch has no changes to `allocation-benchmark/allocations/`), stop and ask the user to add updated allocation dumps. Dumps can be downloaded from the TeamCity build for the release: https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests

---

## Step 2 — Compute per-file allocation diffs

Discover scenarios dynamically from the repository, then run the diff. The JSON files in `allocation-benchmark/allocations/` (excluding `_sites` files and the `client/` subdirectory) are server scenarios; those in `allocation-benchmark/allocations/client/` are client scenarios.

Group files into scenarios by the common prefix before `[` in their filename (e.g. `helloWorld[Jetty].json` and `helloWorld[CIO].json` both belong to scenario `helloWorld`). The part inside `[...]` is the engine name.

Run from the ktor-benchmarks root (replace `OLD_COMMIT` with the value from Step 1):

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py OLD_COMMIT
```

Collect the output carefully — you will need both the per-scenario totals and the per-file diffs for every engine.

---

## Step 3 — Identify significant changes

From the diff output, identify:

**Consistent decreases** — files that decreased by a similar amount across *multiple* engines/scenarios. These point to a shared improvement.

**Consistent increases** — files that increased across multiple scenarios. These are regressions to explain.

**Large outliers** — single-file changes that are much larger than the rest (e.g. 10× bigger). These deserve individual explanation.

Group the file-level changes by likely cause: files that always move together probably share a root cause.

---

## Step 4 — Attribute changes to PRs

The ktor main repository is at **https://github.com/ktorio/ktor**.

**First, locate the ktor repo:** Ask the user if the ktor repository is already cloned on disk and where. If they provide a path, use it. If not, clone it:

```bash
git clone https://github.com/ktorio/ktor /tmp/ktor
```

Then, from the ktor repo directory:

**a) List all commits between the two versions:**
```bash
git log --oneline vOLD_VERSION..vNEW_VERSION
```

**b) For each file showing a significant change, find which commit touched it between the two versions:**
```bash
git log --oneline vOLD_VERSION..vNEW_VERSION -- "**/TheFile.kt"
```

**c) Get PR details for the relevant commits:**
```bash
gh pr view PR_NUMBER --repo ktorio/ktor --json title,body
```

**d) Query YouTrack for issues fixed in this release:**

Use the `search_issues` tool with query: `project: KTOR {Target release}: NEW_VERSION #Resolved`

Cross-reference the YouTrack issues with the commits and PRs to confirm attribution.

**e) Verify attribution using allocation call sites:**

For any change you're unsure about, use `check_sites.py` to see added/removed/changed stack traces for a specific source file (run from the ktor-benchmarks root):

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/check_sites.py OLD_COMMIT \
  "client/streamingResponse[CIO]" \
  ChunkedTransferEncoding.kt
```

The class names and stack frames will confirm exactly what is being allocated and from which call path.

---

## Step 5 — Write the report

Produce a markdown document with this exact structure. Fill in the data from Steps 2–4. The scenario names and engine lists come from what was discovered in Step 2 — do not hardcode them.

```markdown
## Allocation Benchmark Results: OLD_VERSION → NEW_VERSION

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.

### Key Takeaways

**Improvements**

- **[Plain-English description of what improved and why it matters.]**
  [PR_LINK] — one sentence on the mechanism (what was removed/optimized).

- ...

**Regressions**

- **[Plain-English description of what increased and why.]**
  [PR_LINK] — one sentence explaining the trade-off or correctness reason.

- ...

---

### Client

<details>
<summary><code>scenarioA</code> · <code>scenarioB</code> · ...</summary>

#### `scenarioA` — [short description]

<details>
<summary>CIO −X bytes · Apache −X bytes · OkHttp −X bytes · Java −X bytes</summary>

| Engine | Consumed (NEW) | Baseline (OLD) | Saved |
|--------|---------------|----------------|-------|
| CIO    | X KB | X KB | **−X bytes (−X%)** |
| Apache | X KB | X KB | **−X bytes** |
| OkHttp | X KB | X KB | **−X bytes** |
| Java   | X KB | X KB | **−X bytes** |

<details>
<summary>CIO — allocation changes</summary>

**Decreased:**

| File | Change | Old → New |
|------|--------|-----------|
| `FileName.kt` | −X | OLD → NEW |

**Increased:**

| File | Change | Old → New |
|------|--------|-----------|
| `FileName.kt` | +X | OLD → NEW |

</details>

... (repeat for each engine)

</details>

... (repeat for each client scenario)

</details>

---

### Server

<details>
<summary><code>scenarioA</code> · <code>scenarioB</code> · ...</summary>

... (same structure, with engines discovered from the files)

</details>
```

**Key Takeaways writing guidelines:**
- Lead with the **user-visible outcome** ("X no longer allocated per request"), not a file or component name.
- State percentages for large changes (≥5%); use absolute bytes for small ones.
- When multiple PRs combine to produce a consistent per-engine improvement, add one summary bullet with the net effect.
- When a PR causes both a saving and a regression, cover both in **one bullet** as a trade-off.
- Omit items smaller than ~50 bytes across all engines — measurement noise.
- Regressions must state the trade-off or correctness reason.
- If a change is a measurement artefact (e.g. Kotlin codegen line-number shifts), call it out explicitly — don't list it as an improvement or regression.
- If attribution is uncertain, say "cause under investigation".

**Per-engine table formatting:**
- Use `X KB` with two decimal places for totals (e.g. `46.20 KB`).
- In summary lines, list engines in the order: CIO · Apache · OkHttp · Java (client) or Jetty · Tomcat · Netty · CIO (server).
- Show "No increases." or "No decreases." when the section is empty.
- For a value that dropped to zero, write `**0**` to make it prominent.

---

## Step 6 — Save the output

Write the completed report to:
```
allocation-benchmark/NEW_VERSION.md
```

---

## Notes

- The benchmark measures allocations over 300 requests with a 12% tolerance. Small differences (< ~50 bytes per file) may be measurement noise; note this when relevant.
- `_sites.json` files contain full call-site data grouped by top stack frame (source file). Use them to confirm attribution when a change is ambiguous — the class name and stack frames are authoritative.
- When a single PR causes both a large saving and a smaller regression in the same file (e.g. a chunked parser that reads more efficiently but allocates per-chunk state), describe both together as a trade-off in a single bullet.
