## Allocation Benchmark Results: 3.5.2 → (pending)

> ⚠️ This report was generated with AI assistance and may contain incorrect attributions or false claims. Please verify before publishing.

### Key Takeaways

**Improvements**

- **Suppressed client trace logging no longer constructs messages, saving approximately 3.5–3.8 KiB per request across every client engine.**
  [KTOR-9676 / #5769](https://github.com/ktorio/ktor/pull/5769) makes trace logging lazy. Call-site data confirms deterministic reductions of 3,776 bytes for `helloWorld`, 3,672 bytes for `fileResponse`, 3,544 bytes for `gzipResponse`, and 3,608 bytes for `streamingResponse` across `DefaultTransform.kt`, `DefaultRequest.kt`, `DefaultResponseValidation.kt`, `HttpCallValidator.kt`, `URLBuilder.kt`, and `URLUtils.kt`.

**Regressions**

- **OkHttp requests allocate approximately 648 additional bytes to close response bodies away from the cancelling thread.**
  [KTOR-9773 / #5793](https://github.com/ktorio/ktor/pull/5793) replaces the completion-handler close with dispatcher-owned cleanup: approximately +560 bytes in `OkHttpEngine.kt` and +88 bytes when `HttpStatement.kt` resumes the cleanup. The deterministic increase remains present in every OkHttp scenario but is offset in report totals by the lazy-logging improvement and scheduler-dependent pool movements.

**Measurement artifacts**

- `client/helloWorld[OkHttp]`: the raw report total is **+230 bytes**. `OkHttpEngine.kt` includes the deterministic KTOR-9773 increase plus **+3,410 bytes** at the existing request URL-conversion stack, matching [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate); the logging improvement offsets most of both increases.
- `client/streamingResponse[OkHttp]`: **−55,594 bytes** matches [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate). The selected retry was 167,418 bytes lower than the first attempt.
- `client/gzipResponse[Apache]`: `ApacheResponseConsumer.kt` is **+410 bytes**, and `client/streamingResponse[Apache]` is **+349 bytes**, matching [Apache response-queue segment boundaries](known-variations.md#apache-response-queue-segment-boundaries). Their raw report totals are dominated by the logging improvement.
- `client/gzipResponse[Java]`: `ByteChannel.kt` is **+3,334 bytes**, and `client/streamingResponse[Java]` is **+3,624 bytes**, matching [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution) and offsetting most of the logging improvement.
- `client/fileResponse[CIO]`, `client/gzipResponse[CIO]`, and `client/streamingResponse[CIO]` retain correlated movements across `ByteChannel.kt`, `CIOReader.kt`, selector, and write-channel locations matching [CIO socket-read and selector cadence](known-variations.md#cio-socket-read-and-selector-cadence).
- `client/gzipResponse[OkHttp]`: `ByteChannel.kt` is **−2,486 bytes**, matching [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution), while the raw report total is **−5,489 bytes** after the logging improvement and deterministic KTOR-9773 increase.

---

### Client

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code> · <code>gzipResponse</code> · <code>streamingResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 40.21 KB | 44.07 KB | **−3,957 bytes** |
| Apache | 28.88 KB | 32.60 KB | **−3,817 bytes** |
| OkHttp | 25.85 KB | 25.63 KB | **+230 bytes** |
| Java | 24.51 KB | 28.23 KB | **−3,814 bytes** |

#### `fileResponse` — buffered file download

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2361.44 KB | 2364.11 KB | **−2,734 bytes** |
| Apache | 2563.91 KB | 2567.51 KB | **−3,687 bytes** |
| OkHttp | 2342.38 KB | 2345.34 KB | **−3,029 bytes** |
| Java | 2344.54 KB | 2348.26 KB | **−3,808 bytes** |

#### `gzipResponse` — compressed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 6405.79 KB | 6410.44 KB | **−4,765 bytes** |
| Apache | 8347.79 KB | 8351.19 KB | **−3,480 bytes** |
| OkHttp | 6239.42 KB | 6244.78 KB | **−5,489 bytes** |
| Java | 6306.18 KB | 6306.50 KB | **−325 bytes** |

#### `streamingResponse` — streamed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2307.51 KB | 2312.52 KB | **−5,129 bytes** |
| Apache | 4250.33 KB | 4253.57 KB | **−3,327 bytes** |
| OkHttp | 3234.27 KB | 3288.57 KB | **−55,594 bytes** |
| Java | 2208.90 KB | 2208.99 KB | **−94 bytes** |

</details>

---

### Server

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 7.54 KB | 7.54 KB | **0 bytes** |
| Tomcat | 14.71 KB | 14.71 KB | **+7 bytes** |
| Netty | 7.89 KB | 7.88 KB | **+1 byte** |
| CIO | 15.77 KB | 15.77 KB | **0 bytes** |

#### `fileResponse` — static file response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 39.30 KB | 39.35 KB | **−45 bytes** |
| Tomcat | 42.74 KB | 42.77 KB | **−40 bytes** |
| Netty | 34.28 KB | 34.23 KB | **+46 bytes** |
| CIO | 38.64 KB | 38.60 KB | **+45 bytes** |

</details>

> [!TIP]
> To see full per-file allocation diffs, ask Claude: *"show release/3.x allocation diffs for 3.5.2..main"*,
> or run the script manually:
> ```
> python3 .claude/skills/allocations/scripts/compute_diff.py --baseline release/3.x v3.5.2..main
> ```

> Source: [TeamCity build](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/439489)
