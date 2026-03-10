# Release workflow

Finalize `pending.md` into a versioned release report. Follow every step below in order.

---

## Step 1 — Ensure fresh dumps are committed

Verify the `vOLD_VERSION` tag exists:
```bash
git show vOLD_VERSION --no-patch
```

If the tag is missing, stop and ask the user to create it before proceeding.

Check whether new dumps are committed on HEAD:
```bash
git log --oneline -1 -- allocation-benchmark/allocations/
```

If the most recent commit touching `allocations/` is the same commit as `vOLD_VERSION` (i.e. no new dumps have been committed since the last release), follow Steps 1–4 from `references/update.md` to obtain and commit fresh dumps before continuing.

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

