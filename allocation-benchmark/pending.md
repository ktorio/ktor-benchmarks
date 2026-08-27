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

- **Jetty 12.1 increases instrumented HTTP/2 response allocations by 408 bytes for `helloWorld` and 8,551 bytes for `fileResponse`.**
  [#5153](https://github.com/ktorio/ktor/pull/5153) updates Jetty Jakarta from 12.0.35 to 12.1.12. For `fileResponse`, `ArrayList$Itr` contributes +6,176 bytes and new retained-buffer wrappers, callbacks, reference counters, atomics, and `ByteBuffer[]` contribute a net +2,376 bytes at `JettyResponseBodyWriter.kt:50`. The allocation agent makes these objects observable; [isolated JMH measurements](https://github.com/ktorio/ktor-benchmarks/pull/143) show C2 eliminates the iterators in direct `DynamicCapacity` calls, so the instrumented increase must not be interpreted as the production allocation cost.

- **OkHttp requests allocate approximately 648 additional bytes to close response bodies away from the cancelling thread.**
  [KTOR-9773 / #5793](https://github.com/ktorio/ktor/pull/5793) replaces completion-handler closing with dispatcher-owned cleanup: approximately +560 bytes in `OkHttpEngine.kt` and +88 bytes when `HttpStatement.kt` resumes cleanup. The increase is offset by client-wide improvements except where segment-pool variance dominates.

- **CIO server response delivery allocates 72 additional bytes to cancel responses lost to writer timeout cancellation.**
  [KTOR-9761 / #5785](https://github.com/ktorio/ktor/pull/5785) adds an `onUndeliveredElement` handler and explicit cancellation handling so claimed responses cannot leave request handlers suspended indefinitely.

- **CIO request parsing allocates 112 additional bytes in generated state-machine references.**
  [KTOR-9702 / #5741](https://github.com/ktorio/ktor/pull/5741) refactors `readRemaining` through `readBuffer`; the unchanged `internalReadLineTo` path gains one generated `ObjectRef` allocation per parsed request after recompilation.

**Measurement and baseline artifacts**

- The baseline was regenerated locally from Ktor `main` revision [`349d5a1`](https://github.com/ktorio/ktor/commit/349d5a14591c118e10adf203ead08f77193f1ac7) after changing the benchmark to warm each attempt until JVM compilation time remains stable for three request batches. All 72 allocation attempts stabilized before measurement. The allocation agent still reports instrumented allocation sites rather than objects surviving HotSpot escape analysis.
- `client/streamingResponse[OkHttp]` has a raw **+50,674-byte** report delta. `OkHttpEngine.kt` is +56,121 bytes across the existing response-copy and request URL-conversion stacks. This matches [OkHttp segment-pool hit-rate variation](known-variations.md#okhttp-segment-pool-hit-rate), remains within the configured 278,528-byte location bound, and leaves an effective delta of **−5,447 bytes** after the allowance.
- The stabilized `client/helloWorld[OkHttp]` snapshot reverses the previous URL-conversion pool miss: `[B` falls by 3,338 bytes and the paired `okio.Segment` by 16 bytes at the existing stack. The cumulative report now improves by 3,853 bytes.
- Client `ByteChannel.kt` movements of up to approximately 3.7 KiB remain count redistributions among existing `awaitContent`, `readBuffer`, and flush stacks, matching [`ByteChannel` suspension-path redistribution](known-variations.md#bytechannel-suspension-path-redistribution) and [CIO socket-read and selector cadence](known-variations.md#cio-socket-read-and-selector-cadence); no new allocation caller or type appears.
- `client/helloWorld[Java]` returns to 24,567 bytes, exactly matching the pre-stabilization TeamCity baseline. The +276-byte movement from the locally generated baseline consists of existing `ByteChannel.awaitContent` suspension objects (+200 bytes) and a +76-byte redistribution between `JavaHttpResponseBodyHandler.onComplete` and its consumer coroutine. PR #5153 does not modify these paths, so this is recorded as response-completion scheduling variation rather than a Jetty regression.
- Stabilized `helloWorld` snapshots omit 70 bytes of CIO and 80 bytes of Jetty/Netty/Tomcat `PipelinePhase[]` constructor arrays. The pipeline source is unchanged, so this is recorded as a baseline-methodology correction rather than a production-code improvement.
- The +88-byte Tomcat `KtorServlet.kt` movement in the earlier production build restored the same async-context allocation set present in the release baseline. The additional 85 bytes under `ServletWriter.kt` in `fileResponse` were one-time Tomcat/JDK class-loading and locale-resource allocations triggered by `setWriteListener`, not steady-state request processing.

---

### Client

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code> · <code>gzipResponse</code> · <code>streamingResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 39.64 KB | 43.98 KB | **−4,438 bytes (−9.86%)** |
| Apache | 28.20 KB | 32.70 KB | **−4,600 bytes (−13.74%)** |
| OkHttp | 21.95 KB | 25.72 KB | **−3,853 bytes (−14.63%)** |
| Java | 23.99 KB | 28.23 KB | **−4,345 bytes (−15.03%)** |

#### `fileResponse` — buffered file download

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2358.22 KB | 2364.77 KB | **−6,707 bytes** |
| Apache | 2563.05 KB | 2567.76 KB | **−4,821 bytes** |
| OkHttp | 2341.45 KB | 2345.55 KB | **−4,202 bytes** |
| Java | 2343.82 KB | 2348.33 KB | **−4,615 bytes** |

#### `gzipResponse` — compressed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 6405.49 KB | 6409.86 KB | **−4,479 bytes** |
| Apache | 8347.02 KB | 8351.57 KB | **−4,666 bytes** |
| OkHttp | 6241.33 KB | 6242.59 KB | **−1,289 bytes** |
| Java | 6301.68 KB | 6309.77 KB | **−8,293 bytes** |

#### `streamingResponse` — streamed response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| CIO | 2308.29 KB | 2311.34 KB | **−3,129 bytes** |
| Apache | 4249.73 KB | 4253.85 KB | **−4,217 bytes** |
| OkHttp | 3281.31 KB | 3231.83 KB | **+50,674 bytes** |
| Java | 2204.71 KB | 2212.79 KB | **−8,270 bytes** |

</details>

---

### Server

<details>
<summary><code>helloWorld</code> · <code>fileResponse</code></summary>

#### `helloWorld` — small response overhead

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 7.28 KB | 7.54 KB | **−267 bytes** |
| Tomcat | 13.98 KB | 14.62 KB | **−660 bytes** |
| Netty | 7.22 KB | 7.81 KB | **−600 bytes (−7.51%)** |
| CIO | 15.19 KB | 15.60 KB | **−416 bytes** |

#### `fileResponse` — static file response

| Engine | Consumed (pending) | Baseline (3.5.2) | Δ |
|--------|-------------------:|-----------------:|--:|
| Jetty | 47.39 KB | 39.35 KB | **+8,238 bytes (+20.45%)** |
| Tomcat | 42.47 KB | 43.04 KB | **−582 bytes** |
| Netty | 33.85 KB | 34.56 KB | **−727 bytes** |
| CIO | 38.15 KB | 38.74 KB | **−609 bytes** |

</details>

> [!TIP]
> To see full per-file allocation diffs, ask Claude: *"show main allocation diffs for 3.5.2..main"*,
> or run the script manually:
> ```
> python3 .claude/skills/allocations/scripts/compute_diff.py --baseline main v3.5.2..main
> ```

> Production-change source: [TeamCity build](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/443553)
>
> Stabilized baseline source: local Ktor `main` revision [`349d5a1`](https://github.com/ktorio/ktor/commit/349d5a14591c118e10adf203ead08f77193f1ac7)
>
> Jetty 12.1 and Java client update source: [TeamCity build 443924](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests/443924)
