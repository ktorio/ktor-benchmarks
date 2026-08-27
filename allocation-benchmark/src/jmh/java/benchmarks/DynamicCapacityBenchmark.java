package benchmarks;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class DynamicCapacityBenchmark {
    @State(Scope.Thread)
    public static class BufferState {
        private static final int BUFFER_COUNT = 27;
        private static final int BUFFER_SIZE = 8192;

        private RetainableByteBuffer.DynamicCapacity buffer;

        @Setup(Level.Trial)
        public void setUp() {
            buffer = new RetainableByteBuffer.DynamicCapacity(
                ByteBufferPool.NON_POOLING,
                false,
                Long.MAX_VALUE,
                0,
                0
            );
            for (int index = 0; index < BUFFER_COUNT; index++) {
                buffer.add(RetainableByteBuffer.wrap(ByteBuffer.allocate(BUFFER_SIZE)));
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            buffer.release();
        }
    }

    @Benchmark
    public long size(BufferState state) {
        return state.buffer.size();
    }

    @Benchmark
    public int remaining(BufferState state) {
        return state.buffer.remaining();
    }

    @Benchmark
    public boolean hasRemaining(BufferState state) {
        return state.buffer.hasRemaining();
    }

    @Benchmark
    public long repeatedQueries(BufferState state) {
        return state.buffer.size() +
            state.buffer.remaining() +
            (state.buffer.hasRemaining() ? 1 : 0);
    }
}
