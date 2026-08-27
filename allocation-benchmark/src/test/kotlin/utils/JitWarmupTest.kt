package benchmarks.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class JitWarmupTest {
    @Test
    fun `warms until compilation is stable for three batches`() {
        var requestCount = 0
        var compilationTime = 0L

        val result = warmupUntilJitStabilized(
            compilationTimeMilliseconds = { compilationTime },
            timeSource = TestTimeSource(),
        ) {
            requestCount++
            if (requestCount % 50 == 0 && requestCount <= 300) {
                compilationTime++
            }
        }

        assertEquals(450, result.requestCount)
        assertEquals(450, requestCount)
        assertEquals(6.milliseconds, result.compilationTimeDelta)
    }

    @Test
    fun `fails warmup at the time limit`() {
        val timeSource = TestTimeSource()
        var requestCount = 0

        val failure = assertFailsWith<IllegalStateException> {
            warmupUntilJitStabilized(
                compilationTimeMilliseconds = { 0L },
                timeSource = timeSource,
            ) {
                requestCount++
                timeSource += 60.seconds
            }
        }

        assertEquals(50, requestCount)
        assertEquals("JIT compilation did not stabilize within 1m", failure.message)
    }
}
