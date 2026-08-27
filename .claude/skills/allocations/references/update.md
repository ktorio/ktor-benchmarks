# Update workflow

Update the committed allocation baseline (`allocations/`) to reflect merged PRs, so improvements aren't lost in subsequent work. The user may provide hints — PR numbers or YouTrack task IDs — for changes they expect to see.

---

## Step 1 — Get fresh dumps

Choose the approach based on what the user provided — only ask if neither applies:

- User gave a TC build URL → Option B
- User wants to test a local/unreleased build → Option A

### Option A — Run locally

Use when you want to benchmark a specific local Ktor build (e.g. an unreleased change). A Ktor version with a non-zero patch component selects the release baseline derived from its major version; a zero patch component is ambiguous, so ask for `main` or the applicable `release/MAJOR.x` explicitly.

First, if testing against a locally-built Ktor version, publish it to Maven Local from the ktor repository. Resolve the ktor repo path — see **Ktor repo path** in Prerequisites (SKILL.md).

```bash
# In the ktor repo directory
./gradlew publishJvmAndCommonPublications
```

The version being published is in `{ktor-repo}/VERSION`. To use a specific version string, pass `-Pversion=X.Y.Z-SNAPSHOT`.

Then run the benchmarks from the ktor-benchmarks root:

```bash
./gradlew :allocation-benchmark:test
```

This runs all allocation tests and writes new dumps to `allocation-benchmark/build/allocations/` (dumps are saved before assertions, so files are available even if the task fails). If the task fails due to a threshold exceeded, flag it to the user — it may indicate an unexpected regression alongside the intended improvement.

### Option B — Fetch from TeamCity CI

Use when CI already ran the benchmarks and you just want the results.

Resolve **TC CLI** — see Prerequisites in SKILL.md.

Ask the user for a link to the relevant `Ktor_AllocationTests` build. Extract the build ID from the URL — it is the last numeric segment:

```text
https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/413598
                                                                   ^^^^^^ build ID
```

Fetch the build metadata and new dumps:

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/fetch_teamcity_build.py BUILD_ID --json
```

The command accepts either the numeric build ID or the complete build URL. It downloads `new_allocations.zip`, validates its summary and call-site files against the allocation tests reported by TeamCity, and atomically installs them into `allocation-benchmark/build/allocations/`. If that directory already exists, it is retained as a timestamped sibling such as `allocation-benchmark/build/allocations.backup-20260820-102433`. Downloaded TeamCity metadata and the archive are stored under `allocation-benchmark/build/teamcity/BUILD_ID/`.

Use the returned build URL, target branch, allocation baseline, revisions, test counts, and failed allocation reports in the subsequent investigation. For pull requests the target branch determines the baseline; direct builds use the build branch. The command reports the canonical baseline selected by TeamCity, such as `main` or `release-3.x`. Include the build URL in `pending.md` so readers can inspect the raw results manually.

---

## Step 2 — Compute diff

Both options place new dumps in `build/allocations/`, so always run from the ktor-benchmarks root. Set `BASELINE` to `main` or the applicable `release/MAJOR.x` from Step 1:

```bash
ANALYSIS_DIR=allocation-benchmark/build/allocation-analysis
mkdir -p "$ANALYSIS_DIR"
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py \
  --baseline BASELINE --local --json \
  > "$ANALYSIS_DIR/diff.json"
python3 ${CLAUDE_SKILL_DIR}/scripts/check_diff_sites.py \
  "$ANALYSIS_DIR/diff.json" --json \
  > "$ANALYSIS_DIR/sites.json"
```

Keep the complete structured results in these build-local files instead of printing them into the conversation. Use `allocation-benchmark/build/allocation-analysis/diff.json` to capture every scenario and engine, including exact old/new totals, raw deltas, all location deltas, default-tolerance information, and known-variance annotations. Use `allocation-benchmark/build/allocation-analysis/sites.json` for the corresponding allocation types, sizes, and old/new stack traces. Query and reformat the files for readability, but do not reduce the verification packet to only failures or changes outside tolerance.

---

## Step 3 — Verify against hints and update pending.md

Read `${CLAUDE_SKILL_DIR}/references/investigate-diff.md` for how to analyse the diff output.

Default and known tolerances are assertion policy, not permission to skip investigation. Review correlated or offsetting per-location changes even when the report total is within tolerance, especially when the same pattern appears across scenarios.

If the user provided no hints, ask for them before proceeding — PR numbers or YouTrack IDs let you skip the slow deep investigation. If they cannot provide any, proceed with the deep investigation from `investigate-diff.md` using range `v{PREV_VERSION}..HEAD` in the ktor repo, where `PREV_VERSION` is the current `ktor` version from `libs.versions.toml`.

If the user provided PR numbers or task IDs, cross-reference them against the diff:

- **Expected change present?** Confirm that files touched by each hinted PR show a delta. Then run `check_sites.py` on those files (see `investigate-diff.md`) to verify the call site data matches what the PR actually changed — matching file names is not sufficient.
- **Engine-specific PRs:** Before attributing a change to an engine-specific PR (e.g. `ktor-client-apache5`), verify that the benchmark actually uses that engine variant (check `libs.versions.toml` and the test source imports).
- **Unexpected changes?** For any significant change not explained by the hints, run `check_sites.py` to determine the cause **before** presenting findings to the user. Only mark a change as "cause under investigation" if the call sites are also inconclusive.
- **Expected change missing?** Warn the user — the benchmark may not cover that code path, or the change may not affect allocations.

Before changing the baseline, present a verification packet to the user containing:

- a human-readable table or report listing every scenario and engine with old total, new total, and raw delta; the raw `compute_diff.py` output is optional;
- every applied default tolerance or known variance, without hiding the raw change;
- significant correlated or offsetting location changes, including those whose report total remains within tolerance;
- confirmed, missing, and unexplained attributions; and
- the benchmark test outcome, including any failed scenarios.

Prioritize readable tables and grouped findings over dumping verbose command output. Do not replace the complete per-test evidence with only a prose conclusion. Wait for explicit user confirmation after presenting it, then update `allocation-benchmark/pending.md`.

Once the user confirms, update `allocation-benchmark/pending.md`:

If the file does not exist yet, create it with the header. `PREV_VERSION` is the current Ktor version from `libs.versions.toml` in the ktor-benchmarks root (the `ktor` key under `[versions]`):

```markdown
## Allocation Benchmark Results: PREV_VERSION → (pending)

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.
```

Read `${CLAUDE_SKILL_DIR}/references/report-template.md` for the full structure. Add or update the Key Takeaways bullets and per-scenario tables to reflect the confirmed changes. Use the PR hints as attribution. Mark uncertain attributions as "cause under investigation".

If dumps were fetched via Option B (TeamCity), append the following line at the end of the file so readers can inspect the raw results manually:

```markdown
> Source: [TeamCity build](TC_BUILD_URL)
```

---

## Step 4 — Commit the updated baseline

Copy the new dumps into the selected baseline, then commit:

```bash
cp -r allocation-benchmark/build/allocations/. allocation-benchmark/allocations/BASELINE/
git add allocation-benchmark/allocations/BASELINE/ allocation-benchmark/pending.md
git commit -m "Update allocation baselines

Reflects changes from: PR #X, PR #Y (KTOR-NNNN)"
```
