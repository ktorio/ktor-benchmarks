# Update workflow

Update the committed allocation baseline (`allocations/`) to reflect merged PRs, so improvements aren't lost in subsequent work. The user may provide hints — PR numbers or YouTrack task IDs — for changes they expect to see.

---

## Step 1 — Get fresh dumps

Choose the approach based on what the user provided — only ask if neither applies:

- User gave a TC build URL → Option B
- User wants to test a local/unreleased build → Option A

### Option A — Run locally

Use when you want to benchmark a specific local Ktor build (e.g. an unreleased change).

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

Download and extract the new dumps:

```bash
tc run download BUILD_ID --artifact "new_allocations.zip" --dir /tmp/tc-alloc/
unzip -o /tmp/tc-alloc/new_allocations.zip -d allocation-benchmark/build/allocations/
```

The zip preserves the `client/` subdirectory structure.

Save the TC build URL — you will include it in `pending.md` so readers can inspect the raw results manually.

---

## Step 2 — Compute diff

Both options place new dumps in `build/allocations/`, so always run:

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/compute_diff.py --local
```

Collect per-scenario totals and per-file deltas.

---

## Step 3 — Verify against hints and update pending.md

Read `${CLAUDE_SKILL_DIR}/references/investigate-diff.md` for how to analyse the diff output.

If the user provided no hints, ask for them before proceeding — PR numbers or YouTrack IDs let you skip the slow deep investigation. If they cannot provide any, proceed with the deep investigation from `investigate-diff.md` using range `v{PREV_VERSION}..HEAD` in the ktor repo, where `PREV_VERSION` is the current `ktor` version from `libs.versions.toml`.

If the user provided PR numbers or task IDs, cross-reference them against the diff:

- **Expected change present?** Confirm that files touched by each hinted PR show a delta.
- **Unexpected changes?** For any significant change not explained by the hints, run `check_sites.py` (see `investigate-diff.md`) to determine the cause **before** presenting findings to the user. Only mark a change as "cause under investigation" if the call sites are also inconclusive.
- **Expected change missing?** Warn the user — the benchmark may not cover that code path, or the change may not affect allocations.

Summarise findings — confirmed, missing, unexplained — and wait for the user to confirm before proceeding.

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

Copy the new dumps into the baseline, then commit:

```bash
cp -r allocation-benchmark/build/allocations/. allocation-benchmark/allocations/
git add allocation-benchmark/allocations/ allocation-benchmark/pending.md
git commit -m "Update allocation baselines

Reflects changes from: PR #X, PR #Y (KTOR-NNNN)"
```
