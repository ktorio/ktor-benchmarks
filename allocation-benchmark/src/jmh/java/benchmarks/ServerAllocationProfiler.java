package benchmarks;

import com.sun.management.ThreadMXBean;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.profile.ProfilerException;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerAllocationProfiler implements InternalProfiler {
    private final ThreadMXBean threadAllocationBean;
    private Map<Long, Long> allocatedBytesBefore = Collections.emptyMap();

    public ServerAllocationProfiler() throws ProfilerException {
        java.lang.management.ThreadMXBean managementBean = ManagementFactory.getThreadMXBean();
        if (!(managementBean instanceof ThreadMXBean allocationBean)) {
            throw new ProfilerException("Thread allocation monitoring is unavailable");
        }
        if (!allocationBean.isThreadAllocatedMemorySupported()) {
            throw new ProfilerException("Thread allocation monitoring is unsupported");
        }
        if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
            allocationBean.setThreadAllocatedMemoryEnabled(true);
        }
        threadAllocationBean = allocationBean;
    }

    @Override
    public String getDescription() {
        return "Allocated bytes on Jetty and Ktor server threads";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        allocatedBytesBefore = serverThreadAllocatedBytes();
    }

    @Override
    public Collection<? extends Result> afterIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams,
        IterationResult result
    ) {
        Map<Long, Long> allocatedBytesAfter = serverThreadAllocatedBytes();
        if (allocatedBytesBefore.isEmpty()) {
            return Collections.emptyList();
        }
        if (!allocatedBytesBefore.keySet().equals(allocatedBytesAfter.keySet())) {
            throw new IllegalStateException("Jetty server thread set changed during measurement");
        }

        long allocatedBytes = 0;
        for (Map.Entry<Long, Long> entry : allocatedBytesBefore.entrySet()) {
            allocatedBytes += allocatedBytesAfter.get(entry.getKey()) - entry.getValue();
        }
        long measuredOperations = result.getMetadata().getMeasuredOps();
        if (allocatedBytes < 0 || measuredOperations <= 0) {
            throw new IllegalStateException("Invalid server allocation measurement");
        }

        return Collections.singletonList(
            new ScalarResult(
                "server.alloc.rate.norm",
                (double) allocatedBytes / measuredOperations,
                "B/op",
                AggregationPolicy.AVG
            )
        );
    }

    private Map<Long, Long> serverThreadAllocatedBytes() {
        long[] threadIds = threadAllocationBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadAllocationBean.getThreadInfo(threadIds);
        long[] allocatedBytes = threadAllocationBean.getThreadAllocatedBytes(threadIds);
        Map<Long, Long> result = new HashMap<>();

        for (int index = 0; index < threadIds.length; index++) {
            ThreadInfo threadInfo = threadInfos[index];
            if (threadInfo != null && isServerThread(threadInfo.getThreadName())) {
                if (allocatedBytes[index] < 0) {
                    throw new IllegalStateException("Allocation counter unavailable for " + threadInfo.getThreadName());
                }
                result.put(threadIds[index], allocatedBytes[index]);
            }
        }
        return result;
    }

    private boolean isServerThread(String threadName) {
        return threadName.startsWith("qtp") || threadName.startsWith("ktor-jetty-");
    }
}
