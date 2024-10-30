@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor.extend

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import software.amazon.lastmile.kotlin.inject.anvil.test.compile
import software.amazon.lastmile.kotlin.inject.anvil.test.contributesRenderer
import software.amazon.lastmile.kotlin.inject.anvil.test.generatedProperty
import software.amazon.lastmile.kotlin.inject.anvil.test.propertyAnnotations
import kotlin.reflect.KClass

class ContributingAnnotationProcessorTest {

    @Test
    fun `a property is generated in the lookup package for a contributing annotation`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
            import kotlin.annotation.AnnotationTarget.CLASS

            @ContributingAnnotation
            @Target(CLASS)
            annotation class ContributesRenderer
            """,
        ) {
            val property = contributesRenderer.generatedProperty

            // The type parameter gets erased at runtime.
            assertThat(property.type.kotlin).isEqualTo(KClass::class)

            // Checks the returned type.
            assertThat(property.get(null)).isEqualTo(contributesRenderer.kotlin)

            // The @Origin annotation is present.
            assertThat(
                contributesRenderer.propertyAnnotations.filterIsInstance<Origin>().single().value,
            ).isEqualTo(contributesRenderer.kotlin)
        }
    }

    @Test
    fun `a contributing annotation interface must be public`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
            import kotlin.annotation.AnnotationTarget.CLASS

            @ContributingAnnotation
            @Target(CLASS)
            internal annotation class ContributesRenderer
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Contributing annotations must be public.")
        }
    }
}
