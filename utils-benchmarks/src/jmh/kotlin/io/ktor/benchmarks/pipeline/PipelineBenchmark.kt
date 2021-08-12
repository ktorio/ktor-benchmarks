package io.ktor.benchmarks.pipeline

import io.ktor.benchmarks.pipeline.experiment.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.nio.channels.Pipe

val A = PipelinePhase("A")
val B = PipelinePhase("B")
val C = PipelinePhase("C")
val D = PipelinePhase("D")
val phases = listOf(A, B, C, D)

class MyPipeline : Pipeline<String, Blackhole>(A, B, C, D)
class MyExperimentalPipeline : ExperimentalPipeline<String, Blackhole>(A, B, C, D)

class MyDebugPipeline : Pipeline<String, Blackhole>(A, B, C, D) {
    override val developmentMode: Boolean = true
}

@State(Scope.Benchmark)
class PipelineBenchmark {
    lateinit var pipeline: Pipeline<String, Blackhole>
    lateinit var debugPipeline: Pipeline<String, Blackhole>
    lateinit var experimental: ExperimentalPipeline<String, Blackhole>

    @Setup
    fun setup() {
        pipeline = MyPipeline()
        debugPipeline = MyDebugPipeline()
        experimental = MyExperimentalPipeline()

        pipeline.setup()
        debugPipeline.setup()
        experimental.setup()
    }

//    @Benchmark
    fun testPipelineExecute(hole: Blackhole): String = runBlocking {
        pipeline.execute(hole, "")
    }

//    @Benchmark
    fun testDebugPipelineExecute(hole: Blackhole): String = runBlocking {
        debugPipeline.execute(hole, "")
    }

    @Benchmark
    fun testExperimentalPipelineExecute(hole: Blackhole): String = runBlocking {
        experimental.execute(hole, "")
    }
}

val x = MyExperimentalPipeline().apply { setup() }
fun main() = runBlocking {
    val context =
        Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
}

private fun Pipeline<String, Blackhole>.setup() {
    repeat(2) { number ->
        phases.forEach {
            intercept(it) {
                context.consume(subject)
                proceedWith(subject)
            }
        }
    }
}

private fun ExperimentalPipeline<String, Blackhole>.setup() {
    repeat(2) { number ->
        phases.forEach {
            intercept(it) {
                context.consume(subject)
                proceedWith(subject)
            }
        }
    }
}