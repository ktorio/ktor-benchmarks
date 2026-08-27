## Allocation Benchmark Results: 3.5.2 → (pending)

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.

### Key Takeaways

**Improvements**

- **Suppressed client trace logging no longer constructs messages, saving approximately 3.5–3.8 KiB per request across every client engine.**
  [KTOR-9676 / #5769](https://github.com/ktorio/ktor/pull/5769) makes trace logging lazy. Call-site data confirms deterministic reductions of 3,776 bytes for `helloWorld`, 3,672 bytes for `fileResponse`, 3,544 bytes for `gzipResponse`, and 3,608 bytes for `streamingResponse` across `DefaultTransform.kt`, `DefaultRequest.kt`, `DefaultResponseValidation.kt`, `HttpCallValidator.kt`, `URLBuilder.kt`, and `URLUtils.kt`.

- **Client URL encoding avoids temporary encoders and buffers, saving 640 bytes per request on every engine.**
  [KTOR-9778 / #5800](https://github.com/ktorio/ktor/pull/5800) replaces boxed character sets with ASCII bitmasks and writes percent-encoded bytes directly. The call sites show `Codecs.kt` at −560 bytes and `Encoding.kt` at −160 bytes, offset by +80 bytes of result arrays attributed to `Strings.kt`.

- **Server routing no longer allocates suspending continuations for simple paths, reducing these scenarios by 345–600 bytes per request.**
  [#5690](https://github.com/ktorio/ktor/pull/5690) introduces synchronous route evaluation and lazy path segmentation. `RoutingResolveContext.kt` falls by 720 bytes in `helloWorld` and 576 bytes in `fileResponse` on every engine. Lazy decoding adds 96 bytes to `helloWorld`, but all server totals remain lower.

**Regressions and correctness trade-offs**

- **OkHttp requests allocate approximately 648 additional bytes to close response bodies away from the cancelling thread.**
  [KTOR-9773 / #5793](https://github.com/ktorio/ktor/pull/5793) replaces completion-handler closing with dispatcher-owned cleanup: approximately +560 bytes in `OkHttpEngine.kt` and +88 bytes when `HttpStatement.kt` resumes cleanup. The increase is offset by client-wide improvements except where segment-pool variance dominates.

- **CIO server response delivery allocates 72 additional bytes to cancel responses lost to writer timeout cancellation.**
  [KTOR-9761 / #5785](https://github.com/ktorio/ktor/pull/5785) adds an `onUndeliveredElement` handler and explicit cancellation handling so claimed responses cannot leave request handlers suspended indefinitely.

- **CIO request parsing allocates 112 additional bytes in generated state-machine references.**
  [KTOR-9702 / #5741](https://github.com/ktorio/ktor/pull/5741) refactors `readRemaining` through `readBuffer`; the unchanged `internalReadLineTo` path gains one generated `ObjectRef` allocation per parsed request after recompilation.

**Measurement and baseline artifacts**

- `client/streamingResponse[OkHttp]` has a raw **+45,111-byte** report delta. `OkHttpEngine.kt` is +48,041 bytes, dominated by changing `[B` and paired `okio.Segment` counts at the existing `toChannel` response-copy stack. This matches [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate), remains within the configured 278,528-byte bound, and leaves an effective delta of **−2,930 bytes** after the allowance.
- `client/helloWorld[OkHttp]` includes approximately +3.35 KiB of `[B`/`okio.Segment` movement at the existing request URL-conversion stack, also matching [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate). No allowance is consumed because the raw report total still decreases by 497 bytes.
- `ByteChannel.kt` moves by −873 bytes in `client/gzipResponse[CIO]` and by +337/+692/+1,013 bytes in Apache/CIO/OkHttp streaming responses. Comparison with the immediately preceding `main` dump shows only count redistribution among existing `awaitContent`, `readBuffer`, and flush stacks, matching [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution) and [CIO socket-read and selector cadence](known-variations.md#cio-socket-read-and-selector-cadence); no new allocation caller or type appears.
- The +70-byte CIO and +80-byte Netty `PipelinePhase[]` movements restore the constructor vararg arrays already present in the release baseline. The old `main` dumps omitted these arrays; the new call sites match the stable paired send/receive pipeline allocations rather than a production-code change.
- The +88-byte Tomcat `KtorServlet.kt` movement likewise restores the same async-context allocation set present in the release baseline. The additional 85 bytes under `ServletWriter.kt` in `fileResponse` are one-time Tomcat/JDK class-loading and locale-resource allocations triggered by `setWriteListener`, not steady-state request processing.
- [KTOR-9704 / #5743](https://github.com/ktorio/ktor/pull/5743) is already represented by the previous baseline and has no independent allocation delta in this comparison. The new `Strings.kt` entries are calls introduced by KTOR-9778.

---

### Client

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code> · <code>gzipResponse</code> · <code>streamingResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 39.65 KB | 43.98 KB | **−4,432 bytes (−9.84%)** |
| Apache | 28.30 KB | 32.70 KB | **−4,496 bytes (−13.43%)** |
| OkHttp | 25.23 KB | 25.72 KB | **−497 bytes** |
| Java | 23.99 KB | 28.44 KB | **−4,552 bytes (−15.63%)** |

#### `fileResponse` — buffered file download

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2360.14 KB | 2364.77 KB | **−4,740 bytes** |
| Apache | 2563.37 KB | 2567.76 KB | **−4,498 bytes** |
| OkHttp | 2341.89 KB | 2345.55 KB | **−3,744 bytes** |
| Java | 2343.94 KB | 2348.33 KB | **−4,491 bytes** |

#### `gzipResponse` — compressed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 6404.99 KB | 6409.86 KB | **−4,982 bytes** |
| Apache | 8347.23 KB | 8351.57 KB | **−4,444 bytes** |
| OkHttp | 6238.98 KB | 6242.59 KB | **−3,690 bytes** |
| Java | 6305.33 KB | 6309.77 KB | **−4,549 bytes** |

#### `streamingResponse` — streamed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2307.81 KB | 2311.34 KB | **−3,623 bytes** |
| Apache | 4249.95 KB | 4253.85 KB | **−3,996 bytes** |
| OkHttp | 3275.88 KB | 3231.83 KB | **+45,111 bytes** |
| Java | 2208.38 KB | 2212.79 KB | **−4,517 bytes** |

</details>

---

### Server

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 6.96 KB | 7.54 KB | **−600 bytes (−7.77%)** |
| Tomcat | 14.13 KB | 14.62 KB | **−503 bytes** |
| Netty | 7.30 KB | 7.81 KB | **−520 bytes (−6.51%)** |
| CIO | 15.26 KB | 15.60 KB | **−345 bytes** |

#### `fileResponse` — static file response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 39.12 KB | 39.69 KB | **−581 bytes** |
| Tomcat | 42.66 KB | 43.04 KB | **−395 bytes** |
| Netty | 34.06 KB | 34.56 KB | **−519 bytes** |
| CIO | 38.39 KB | 38.74 KB | **−360 bytes** |

</details>

> [!TIP]
> To see full per-file allocation diffs, ask Claude: *"show main allocation diffs for 3.5.2..main"*,
> or run the script manually:
> ```
> python3 .claude/skills/allocations/scripts/compute_diff.py --baseline main v3.5.2..main
> ```

> Source: [TeamCity build](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/443553)
