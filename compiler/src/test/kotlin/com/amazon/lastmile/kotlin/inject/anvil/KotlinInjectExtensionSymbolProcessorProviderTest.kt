@file:OptIn(ExperimentalCompilerApi::class)

package com.amazon.lastmile.kotlin.inject.anvil

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class KotlinInjectExtensionSymbolProcessorProviderTest {

    @Test
    fun `a processor can be disabled`() {
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                processorOptions = mapOf(
                    "com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesToProcessor" to
                        "disabled",
                ),
            )
            .compile(
                """
                package com.amazon.test
    
                import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
    
                @ContributesTo
                @SingleInAppScope
                interface ComponentInterface
                """,
            )
            .run {
                assertThat(exitCode).isEqualTo(OK)

                // Throws the error because the generated component cannot be found.
                assertFailure {
                    componentInterface.generatedComponent
                }.isInstanceOf<ClassNotFoundException>()
            }
    }
}
