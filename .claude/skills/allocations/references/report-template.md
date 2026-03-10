# Report template

Use this structure for both release reports and `pending.md`. Scenario names and engine lists come from `compute_diff.py` output — do not hardcode them.

For `pending.md`, replace the header version line with `PREV_VERSION → (pending)` as instructed in `update.md` Step 3. In the TIP block, use `main` as `NEW_VERSION` (e.g. `OLD_VERSION..main` / `vOLD_VERSION..main`) since the new version has no tag yet.

```markdown
## Allocation Benchmark Results: OLD_VERSION → NEW_VERSION

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.

### Key Takeaways

**Improvements**

- **[Plain-English description of what improved and why it matters.]**
  [KTOR-XXXX / #YYYY](PR_URL) — one sentence on the mechanism (what was removed/optimized). Omit the YouTrack part if no issue is associated.

**Regressions**

- **[Plain-English description of what increased and why.]**
  [PR_LINK] — one sentence explaining the trade-off or correctness reason.

---

### Client

<details>
<summary><code>scenarioA</code> · <code>scenarioB</code> · ...</summary>

#### `scenarioA` — [short description]

| Engine | Consumed (NEW) | Baseline (OLD) | Δ |
|--------|---------------|----------------|---|
| CIO    | X KB | X KB | **−X bytes (−X%)** |
| Apache | X KB | X KB | **−X bytes** |
| OkHttp | X KB | X KB | **−X bytes** |
| Java   | X KB | X KB | **−X bytes** |

... (repeat for each client scenario)

</details>

---

### Server

<details>
<summary><code>scenarioA</code> · <code>scenarioB</code> · ...</summary>

... (same structure, with engines discovered from the files)

</details>

> [!TIP]
> To see full per-file allocation diffs, ask Claude: *"show allocation diffs for OLD_VERSION..NEW_VERSION"*,
> or run the script manually:
> ```
> python3 .claude/skills/allocations/scripts/compute_diff.py vOLD_VERSION..vNEW_VERSION
> ```
```

---

## Key Takeaways guidelines

- Lead with the **user-visible outcome** ("X no longer allocated per request"), not a file or component name.
- State percentages for large changes (≥5%); use absolute bytes for small ones.
- When multiple PRs combine to produce a consistent per-engine improvement, add one summary bullet with the net effect.
- When a PR causes both a saving and a regression, cover both in **one bullet** as a trade-off.
- Omit items smaller than ~50 bytes across all engines — measurement noise.
- Regressions must state the trade-off or correctness reason.
- If a change is a measurement artefact (e.g. Kotlin codegen line-number shifts), call it out explicitly.
- If attribution is uncertain, say "cause under investigation".

## Per-engine table formatting

- Use `X KB` with two decimal places for totals (e.g. `46.20 KB`).
- Engine order: CIO · Apache · OkHttp · Java (client) or Jetty · Tomcat · Netty · CIO (server).
- State percentages for large per-scenario changes (≥5%); omit for small ones.
