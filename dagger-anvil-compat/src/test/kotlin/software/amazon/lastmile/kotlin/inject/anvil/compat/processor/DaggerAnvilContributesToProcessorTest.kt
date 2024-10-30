@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.compat.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compat.OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS
import software.amazon.lastmile.kotlin.inject.anvil.compat.createUnsupportedParamMessage
import software.amazon.lastmile.kotlin.inject.anvil.test.compile
import software.amazon.lastmile.kotlin.inject.anvil.test.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.test.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.test.inner
import software.amazon.lastmile.kotlin.inject.anvil.test.origin

class DaggerAnvilContributesToProcessorTest {

    @Test
    fun `a component interface is generated in the lookup package for a contributed component interface`() {
        compile(
            """
            package software.amazon.test
    
            import com.squareup.anvil.annotations.ContributesTo

            @ContributesTo(Unit::class)
            interface ComponentInterface
            """,
        ) {
            val generatedComponent = componentInterface.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(componentInterface)
            assertThat(generatedComponent.origin).isEqualTo(componentInterface)
        }
    }

    @Test
    fun `a component interface is generated in the lookup package for a contributed inner component interface`() {
        compile(
            """
            package software.amazon.test
    
            import com.squareup.anvil.annotations.ContributesTo

            interface ComponentInterface {
                @ContributesTo(Unit::class)
                interface Inner
            }
            """,
        ) {
            val generatedComponent = componentInterface.inner.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(componentInterface.inner)
            assertThat(generatedComponent.origin).isEqualTo(componentInterface.inner)
        }
    }

    @Test
    fun `a contributed component interface must be public`() {
        compile(
            """
            package software.amazon.test
    
            import com.squareup.anvil.annotations.ContributesTo

            @ContributesTo(Unit::class)
            private interface ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Contributed component interfaces must be public.")
        }
    }

    @Test
    fun `only interfaces can be contributed`() {
        compile(
            """
            package software.amazon.test
    
            import com.squareup.anvil.annotations.ContributesTo

            @ContributesTo(Unit::class)
            abstract class ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Only interfaces can be contributed.")
        }
    }

    @Test
    fun `warn when using unsupported parameters on anvil annotation`() {
        compile(
            """
            package software.amazon.test
    
            import com.squareup.anvil.annotations.ContributesTo
            @ContributesTo(Unit::class, replaces = [String::class])
            interface ComponentInterface
            """,
        ) {
            val expectedMessage = createUnsupportedParamMessage("ContributesTo", "replaces")
            assertThat(messages).contains(expectedMessage)
        }
    }

    @Test
    fun `ignore warning when using unsupported parameters on anvil annotation`() {
        compile(
            """
            package software.amazon.test

            import com.squareup.anvil.annotations.ContributesTo
            @ContributesTo(Unit::class, replaces = [String::class])
            interface ComponentInterface
            """,
            options = mapOf(OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS to "true"),
        ) {
            assertThat(messages).isEmpty()
        }
    }
}
