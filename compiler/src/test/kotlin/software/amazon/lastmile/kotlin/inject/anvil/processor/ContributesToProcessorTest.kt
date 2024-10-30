@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.OPTION_IGNORE_ANVIL_UNSUPPORTED_PARAM_WARNINGS
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.inner
import software.amazon.lastmile.kotlin.inject.anvil.origin

class ContributesToProcessorTest {

    @ParameterizedTest(name = "annotation: {0}")
    @ValueSource(
        strings = [
            "software.amazon.lastmile.kotlin.inject.anvil.ContributesTo",
            "com.squareup.anvil.annotations.ContributesTo",
        ],
    )
    fun `a component interface is generated in the lookup package for a contributed component interface`(
        annotationFqName: String,
    ) {
        compile(
            """
            package software.amazon.test
    
            import $annotationFqName

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

    @ParameterizedTest(name = "annotation: {0}")
    @ValueSource(
        strings = [
            "software.amazon.lastmile.kotlin.inject.anvil.ContributesTo",
            "com.squareup.anvil.annotations.ContributesTo",
        ],
    )
    fun `a component interface is generated in the lookup package for a contributed inner component interface`(
        annotationFqName: String,
    ) {
        compile(
            """
            package software.amazon.test
    
            import $annotationFqName

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

    @ParameterizedTest(name = "annotation: {0}")
    @ValueSource(
        strings = [
            "software.amazon.lastmile.kotlin.inject.anvil.ContributesTo",
            "com.squareup.anvil.annotations.ContributesTo",
        ],
    )
    fun `a contributed component interface must be public`(annotationFqName: String) {
        compile(
            """
            package software.amazon.test
    
            import $annotationFqName

            @ContributesTo(Unit::class)
            private interface ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Contributed component interfaces must be public.")
        }
    }

    @ParameterizedTest(name = "annotation: {0}")
    @ValueSource(
        strings = [
            "software.amazon.lastmile.kotlin.inject.anvil.ContributesTo",
            "com.squareup.anvil.annotations.ContributesTo",
        ],
    )
    fun `only interfaces can be contributed`(annotationFqName: String) {
        compile(
            """
            package software.amazon.test
    
            import $annotationFqName

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
            val expectedMessage = ContributesToProcessor.createUnsupportedParamMessage("replaces")
            // The warning contains the file, line and column information. We don't care about that.
            assertThat(messages).containsMatch(Regex(".*$expectedMessage"))
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
            options = mapOf(OPTION_IGNORE_ANVIL_UNSUPPORTED_PARAM_WARNINGS to "true"),
        ) {
            assertThat(messages).isEmpty()
        }
    }
}
