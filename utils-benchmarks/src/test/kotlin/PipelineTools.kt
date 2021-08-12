import io.ktor.util.pipeline.*


val A = PipelinePhase("A")
val B = PipelinePhase("B")
val C = PipelinePhase("C")
val D = PipelinePhase("D")
val phases = listOf(A, B, C, D)

class MyPipeline : Pipeline<String, String>(A, B, C, D)

class MyDebugPipeline : Pipeline<String, String>(A, B, C, D) {
    override val developmentMode: Boolean = true
}

fun Pipeline<String, String>.setup() {
    repeat(100) { number ->
        phases.forEach {
            intercept(it) {
                proceedWith(subject)
            }
        }
    }
}
