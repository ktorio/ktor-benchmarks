# Known allocation measurement artifacts

Allocation totals are sensitive to scheduler timing, pool state, and the shared benchmark runtime. This file documents recurring patterns that can be classified as measurement artifacts **only after their allocation call sites match the pattern below**.

A matching total or source-file name is not sufficient. Always inspect the sites:

```bash
# New snapshots are in build/allocations/
python3 .claude/skills/allocations/scripts/check_sites.py --local \
  "client/SCENARIO[ENGINE]" FileName.kt

# Compare committed revisions
python3 .claude/skills/allocations/scripts/check_sites.py vOLD..vNEW \
  "client/SCENARIO[ENGINE]" FileName.kt
```

Keep the raw delta in reports even when it matches a known artifact. Investigate normally if the relevant production source changed, a new stack trace appeared, or the pattern does not satisfy the checks below.

## OkHttp segment-pool hit rate

**Usually visible in:** `client/streamingResponse[OkHttp]`, occasionally other OkHttp response scenarios.

**Cause:** scheduler timing changes whether Okio/Ktor body-copy buffers are reused or newly allocated.

**Observable call-site pattern:**

- `OkHttpEngine.kt` gains or loses `[B` allocations under the `toChannel` response-body copy path.
- The delta is dominated by approximately 8 KiB arrays and is close to a whole-number multiple of that size.
- The same body-copy stack exists on both sides; only its count changes.
- Smaller `ByteChannel.kt` suspension-count movements may offset part of the array delta.

**Classify as this artifact only when:**

1. `check_sites.py` points to the existing `OkHttpEngine.toChannel` body-copy stack.
2. Raw `_sites.json` confirms that the dominant type is `[B` and that the count changed.
3. No new buffer-allocation stack was introduced by the compared source range.
4. Any deterministic OkHttp changes at other sites are accounted for separately.

Investigate as a regression when the increase comes from another stack, includes new allocation types, or is not explained by the changed array count.

The configured bound for `client/streamingResponse[OkHttp] / OkHttpEngine.kt` is stored in `allocations/tolerances.json`. The bound is assertion policy, not permission to skip the call-site checks.

## `ByteChannel` suspension-path redistribution

**Usually visible in:** compressed and streaming client responses across CIO, Java, Apache, and OkHttp.

**Cause:** producer/consumer timing changes whether a channel read completes directly or suspends and resumes later.

**Observable call-site pattern:**

- `ByteChannel.kt` changes are concentrated under `awaitContent`, `readBuffer`, `readRemaining`, `toByteArray`, or `sleepWhile` paths.
- Existing continuation/state-machine allocations move between direct stacks and stacks containing `invokeSuspend`, `resumeWith`, or `SavedCall`.
- Common types include `CancellableContinuationImpl`, `DispatchedContinuation`, and generated coroutine state-machine objects.
- Paired increases and decreases often appear in `ByteReadChannelOperations.kt` or `ByteWriteChannelOperations.kt` while the response payload and request count remain unchanged.

**Classify as this artifact only when:**

1. The compared Ktor range does not change the relevant channel implementation.
2. Sites are existing suspension/resumption paths rather than a new caller.
3. The movement is primarily a count redistribution between those paths.
4. Large pooled byte-array changes are analyzed separately as pool-hit-rate variation.

A net `ByteChannel.kt` delta by itself is not enough. A new caller, a new continuation type, or changed channel source requires normal investigation.

## CIO socket-read and selector cadence

**Usually visible in:** `client/fileResponse[CIO]`, `client/gzipResponse[CIO]`, and `client/streamingResponse[CIO]`.

**Cause:** socket readiness and coroutine scheduling change the number of read, flush, and selector resumptions needed to consume the same response.

**Observable call-site pattern:**

- Correlated movements appear across several of:
  - `CIOReader.kt`
  - `ByteWriteChannelOperations.jvm.kt`
  - `ActorSelectorManager.kt`
  - `SelectorManagerSupport.kt`
  - `ByteChannel.kt`
- The stacks remain in the existing socket-read, channel-flush, and selector-resume loops.
- Increases in reader/selector files are commonly offset by decreases in `ByteChannel.kt`, or vice versa.
- No single new allocation site explains the total.

**Classify as this artifact only when:**

1. The correlated network/channel files are unchanged in the compared source range.
2. Call-site counts move on existing read/select loops.
3. Request routing and connection mode are unchanged.
4. Any change in request-body or connection-close logic is checked independently.

Do not attribute a CIO delta to this artifact merely because several CIO files changed; the correlation and existing call paths must both be present.

## Apache response-queue segment boundaries

**Usually visible in:** `client/gzipResponse[Apache]` and `client/streamingResponse[Apache]`.

**Cause:** producer/consumer timing changes whether `ApacheResponseConsumer`'s coroutine channel crosses an internal queue-segment boundary.

**Observable call-site pattern:**

- Allocations appear or disappear at the existing `messagesQueue.trySend(src.copy())` site in `ApacheResponseConsumer.kt`.
- `Object[]`, `ChannelSegment`, and `AtomicReferenceArray` allocations move together in whole segment groups.
- The response-consumer source is unchanged and the underlying callback/task count remains otherwise stable.
- `ByteChannel.kt` suspension changes may partially offset the queue-segment delta.

**Classify as this artifact only when:**

1. Raw `_sites.json` shows the segment backing structures at the same `trySend` stack.
2. Their counts move together as a queue-segment group.
3. No new Apache response-consumer call path was introduced.

`check_sites.py` groups normalized stack traces, so different allocated types at the same source line can be displayed under one type. Inspect the raw `_sites.json` entries before concluding that a single type caused the complete delta.

## Shared server test classpath scanning

**Usually visible in:** `server/fileResponse` across several or all server engines.

**Cause:** every server engine benchmark runs on the same test runtime classpath. Adding a dependency for one engine can make static-content resource discovery scan additional classpath entries for every engine.

**Observable call-site pattern:**

- A similar delta appears across multiple server engines.
- The common location is `StaticContentResolution.kt` under `ClassLoader.getResources` or equivalent resource enumeration.
- The engine-specific static-content implementation is unchanged, while the benchmark runtime classpath changed.

**Classify as this artifact only when:**

1. Call sites point to classpath resource enumeration.
2. The cross-engine deltas are consistent with the same added or removed classpath entries.
3. Static-content source changes do not independently explain the movement.

This artifact is deterministic for a given classpath rather than scheduler volatility, but it describes benchmark-runtime growth rather than per-engine request processing.

## Adding a known artifact

Add an entry only after the pattern has recurred or a same-revision rerun has reversed/reproduced it. Document:

1. affected scenarios and engines;
2. the runtime mechanism;
3. exact source files, functions, allocation types, and stack pattern;
4. checks that distinguish it from a real regression; and
5. any bounded tolerance configured in `allocations/tolerances.json`.

A tolerance should be report- and location-specific, bounded from observed evidence, and reference the corresponding section in this file.
