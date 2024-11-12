@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.test

import assertk.assertThat
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.addPreviousResultToClasspath
import com.tschuchort.compiletesting.configureKsp
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.ServiceLoader

/**
 * A simple API over a [KotlinCompilation] with extra configuration support for KSP processors.
 */
// Inspired by Anvil: https://github.com/square/anvil/blob/97e2cc0430311c6b0ed5341da95bb243b582fab8/compiler-utils/src/testFixtures/java/com/squareup/anvil/compiler/internal/testing/AnvilCompilation.kt
class Compilation internal constructor(
    val kotlinCompilation: KotlinCompilation,
) {

    private var isCompiled = false
    private var processorsConfigured = false

    /**
     * Configures the behavior of this compilation.
     */
    fun configureKotlinInjectAnvilProcessor(
        processorOptions: Map<String, String> = emptyMap(),
        symbolProcessorProviders: Set<SymbolProcessorProvider> = emptySet(),
        useKsp2: Boolean = true,
    ): Compilation = apply {
        checkNotCompiled()
        check(!processorsConfigured) { "Processor should not be configured twice." }

        processorsConfigured = true

        with(kotlinCompilation) {
            if (!useKsp2) {
                languageVersion = "1.9"
            }

            configureKsp(useKsp2 = useKsp2) {
                this.symbolProcessorProviders += ServiceLoader.load(
                    SymbolProcessorProvider::class.java,
                    SymbolProcessorProvider::class.java.classLoader,
                )

                this.symbolProcessorProviders += symbolProcessorProviders
                this.processorOptions.putAll(processorOptions)

                // Run KSP embedded directly within this kotlinc invocation
                this.withCompilation = true
                this.incremental = true
            }
        }
    }

    /**
     * Adds the given sources to this compilation with their packages and names inferred.
     */
    fun addSources(@Language("kotlin") vararg sources: String): Compilation = apply {
        checkNotCompiled()
        kotlinCompilation.sources += sources.mapIndexed { index, content ->
            val packageDir = content.lines()
                .firstOrNull { it.trim().startsWith("package ") }
                ?.substringAfter("package ")
                ?.replace('.', '/')
                ?.let { "$it/" }
                ?: ""

            val name = "${kotlinCompilation.workingDir.absolutePath}/sources/src/main/java/" +
                "$packageDir/Source$index.kt"

            Files.createDirectories(File(name).parentFile.toPath())

            SourceFile.kotlin(name, contents = content, trimIndent = true)
        }
    }

    /**
     * Adds the given [result] to the compilation classpath.
     */
    fun addPreviousCompilationResult(result: JvmCompilationResult): Compilation = apply {
        checkNotCompiled()
        kotlinCompilation.addPreviousResultToClasspath(result)
    }

    private fun checkNotCompiled() {
        check(!isCompiled) {
            "Already compiled! Create a new compilation if you want to compile again."
        }
    }

    /**
     * Compiles the underlying [KotlinCompilation]. Note that if
     * [configureKotlinInjectAnvilProcessor] has not been called prior to this, it will be
     * configured with default behavior.
     */
    fun compile(
        @Language("kotlin") vararg sources: String,
        block: JvmCompilationResult.() -> Unit = {},
    ): JvmCompilationResult {
        checkNotCompiled()
        if (!processorsConfigured) {
            // Configure with default behaviors
            configureKotlinInjectAnvilProcessor()
        }
        addSources(*sources)
        isCompiled = true

        return kotlinCompilation.compile().apply(block)
    }

    companion object {
        operator fun invoke(): Compilation {
            return Compilation(
                KotlinCompilation().apply {
                    // Sensible default behaviors
                    inheritClassPath = true
                    jvmTarget = JvmTarget.JVM_1_8.description
                    verbose = false
                },
            )
        }
    }
}

/**
 * Helpful for testing code generators in unit tests end to end.
 *
 * This covers common cases, but is built upon reusable logic in [Compilation] and
 * [Compilation.configureKotlinInjectAnvilProcessor]. Consider using those APIs if more
 * advanced configuration is needed.
 */
fun compile(
    @Language("kotlin") vararg sources: String,
    allWarningsAsErrors: Boolean = true,
    messageOutputStream: OutputStream = System.out,
    workingDir: File? = null,
    previousCompilationResult: JvmCompilationResult? = null,
    moduleName: String? = null,
    useKsp2: Boolean = true,
    multiplatform: Boolean = false,
    options: Map<String, String> = emptyMap(),
    exitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    block: JvmCompilationResult.() -> Unit = { },
): JvmCompilationResult {
    return Compilation()
        .apply {
            kotlinCompilation.apply {
                this.allWarningsAsErrors = allWarningsAsErrors
                this.messageOutputStream = messageOutputStream
                if (workingDir != null) {
                    this.workingDir = workingDir
                }
                if (moduleName != null) {
                    this.moduleName = moduleName
                }
                this.multiplatform = multiplatform
            }

            if (previousCompilationResult != null) {
                addPreviousCompilationResult(previousCompilationResult)
            }
        }
        .configureKotlinInjectAnvilProcessor(useKsp2 = useKsp2, processorOptions = options)
        .compile(*sources)
        .also {
            if (exitCode == KotlinCompilation.ExitCode.OK) {
                assertThat(it.exitCode).isOk()
            } else {
                assertThat(it.exitCode).isError()
            }
        }
        .also(block)
}
