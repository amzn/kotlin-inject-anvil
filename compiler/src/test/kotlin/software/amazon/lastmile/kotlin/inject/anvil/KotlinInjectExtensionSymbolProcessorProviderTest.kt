@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.test.Compilation
import software.amazon.lastmile.kotlin.inject.anvil.test.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.test.generatedComponent

class KotlinInjectExtensionSymbolProcessorProviderTest {

    @Test
    fun `a processor can be disabled`() {
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                processorOptions = mapOf(
                    "software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesToProcessor"
                        to "disabled",
                ),
            )
            .compile(
                """
                package software.amazon.test
    
                import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
                import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
    
                @ContributesTo(Unit::class)
                @SingleIn(Unit::class)
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
