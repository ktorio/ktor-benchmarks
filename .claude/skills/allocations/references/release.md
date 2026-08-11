# Release workflow

Finalize `pending.md` into a versioned release report. Follow every step below in order.

---

## Step 1 — Ensure fresh dumps are committed

Verify the `vOLD_VERSION` tag exists:
```bash
git show vOLD_VERSION --no-patch
```

If the tag is missing, stop and ask the user to create it before proceeding.

Derive `BASELINE` as `release/MAJOR.x` and `BASELINE_DIR` as `release-MAJOR.x` from `OLD_VERSION`, then check whether new release dumps are committed on HEAD:
```bash
git log --oneline -1 -- allocation-benchmark/allocations/BASELINE_DIR/
```

If the most recent commit touching `allocations/` is the same commit as `vOLD_VERSION` (i.e. no new dumps have been committed since the last release), follow Steps 1–4 from `references/update.md` to obtain and commit fresh dumps before continuing.

Before promoting the report, verify that the user has seen and explicitly approved the verification packet required by Step 3 of `references/update.md` for these exact dumps. If not, run the diff against `vOLD_VERSION..HEAD` with `--baseline BASELINE`, investigate significant and correlated location changes, present the complete per-test evidence, and wait for explicit confirmation. A committed baseline or passing tolerance check does not replace this approval.

---

## Step 2 — Promote pending.md to a versioned report

Rename the file and update its header line:

```bash
git mv allocation-benchmark/pending.md allocation-benchmark/NEW_VERSION.md
```

Update the header line from:

```markdown
## Allocation Benchmark Results: OLD_VERSION → (pending)
```

to:

```markdown
## Allocation Benchmark Results: OLD_VERSION → NEW_VERSION
```

---

## Step 3 — Bump the version in libs.versions.toml

Update the `ktor` key under `[versions]` in `libs.versions.toml` (at the ktor-benchmarks root) to `NEW_VERSION`.

---

## Step 4 — Commit

```bash
git add allocation-benchmark/NEW_VERSION.md libs.versions.toml
git commit -m "Add allocation report for NEW_VERSION"
```

