## Allocation Benchmark Results: 3.5.2 → (pending)

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.

### Key Takeaways

**Regressions**

- **OkHttp requests allocate approximately 648 additional bytes to close response bodies away from the cancelling thread.**
  [KTOR-9773 / #5793](https://github.com/ktorio/ktor/pull/5793) replaces the completion-handler close with dispatcher-owned cleanup: approximately +560 bytes in `OkHttpEngine.kt` and +88 bytes when `HttpStatement.kt` resumes the cleanup. The small `helloWorld` case exposes the full cost (+636 bytes, +505 above its default tolerance); the same deterministic increase is present in larger response scenarios.

**Measurement artifacts**

- `client/streamingResponse[OkHttp]`: **−55,831 bytes** matches [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate).
- `client/gzipResponse[Apache]`: **+243 bytes** and `client/streamingResponse[Apache]`: **+306 bytes** match [Apache response-queue segment boundaries](known-variations.md#apache-response-queue-segment-boundaries).
- `client/gzipResponse[Java]`: **+3,104 bytes** and `client/streamingResponse[Java]`: **+3,279 bytes** match [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution).
- `client/fileResponse[CIO]`: **+820 bytes**, `client/gzipResponse[CIO]`: **−1,585 bytes**, and `client/streamingResponse[CIO]`: **−1,635 bytes** match [CIO socket-read and selector cadence](known-variations.md#cio-socket-read-and-selector-cadence).
- `client/gzipResponse[OkHttp]`: **−1,189 bytes** includes a `ByteChannel.kt` decrease matching [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution), partially offset by the confirmed KTOR-9773 increase.

---

### Client

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code> · <code>gzipResponse</code> · <code>streamingResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 44.02 KB | 44.07 KB | **−58 bytes** |
| Apache | 32.60 KB | 32.60 KB | **−1 byte** |
| OkHttp | 26.25 KB | 25.63 KB | **+636 bytes** |
| Java | 28.23 KB | 28.23 KB | **−1 byte** |

#### `fileResponse` — buffered file download

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2364.91 KB | 2364.11 KB | **+820 bytes** |
| Apache | 2567.62 KB | 2567.51 KB | **+110 bytes** |
| OkHttp | 2346.04 KB | 2345.34 KB | **+722 bytes** |
| Java | 2348.10 KB | 2348.26 KB | **−157 bytes** |

#### `gzipResponse` — compressed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 6408.89 KB | 6410.44 KB | **−1,585 bytes** |
| Apache | 8351.43 KB | 8351.19 KB | **+243 bytes** |
| OkHttp | 6243.61 KB | 6244.78 KB | **−1,189 bytes** |
| Java | 6309.53 KB | 6306.50 KB | **+3,104 bytes** |

#### `streamingResponse` — streamed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2310.92 KB | 2312.52 KB | **−1,635 bytes** |
| Apache | 4253.87 KB | 4253.57 KB | **+306 bytes** |
| OkHttp | 3234.04 KB | 3288.57 KB | **−55,831 bytes** |
| Java | 2212.19 KB | 2208.99 KB | **+3,279 bytes** |

</details>

---

### Server

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 7.54 KB | 7.54 KB | **0 bytes** |
| Tomcat | 14.71 KB | 14.71 KB | **+8 bytes** |
| Netty | 7.89 KB | 7.88 KB | **+1 byte** |
| CIO | 15.77 KB | 15.77 KB | **−1 byte** |

#### `fileResponse` — static file response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 39.30 KB | 39.35 KB | **−43 bytes** |
| Tomcat | 42.74 KB | 42.77 KB | **−34 bytes** |
| Netty | 34.30 KB | 34.23 KB | **+67 bytes** |
| CIO | 38.64 KB | 38.60 KB | **+46 bytes** |

</details>

> [!TIP]
> To see full per-file allocation diffs, ask Claude: *"show allocation diffs for 3.5.2..main"*,
> or run the script manually:
> ```
> python3 .claude/skills/allocations/scripts/compute_diff.py v3.5.2..main
> ```
