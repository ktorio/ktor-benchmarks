package allocations

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.newInstance
import org.gradle.process.CommandLineArgumentProvider

fun Project.createJavaAgentProvider(
    configurationName: String,
    dependency: Provider<MinimalExternalModuleDependency>,
): CommandLineArgumentProvider {
    @Suppress("UnstableApiUsage")
    val coroutinesDebugAgent = configurations.resolvable(configurationName) {
        isTransitive = false
    }
    dependencies.add(configurationName, dependency)

    return objects.newInstance<JavaAgentArgumentProvider>().apply {
        classpath.from(coroutinesDebugAgent)
    }
}

private abstract class JavaAgentArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf("-javaagent:${classpath.singleFile.absolutePath}")
}