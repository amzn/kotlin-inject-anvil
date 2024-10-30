@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.test.compile

// Note that there's no unit test verifying the correctly generated code. We're blocked on testing
// expect-actual multiplatform code in unit tests.
//
// https://github.com/ZacSweers/kotlin-compile-testing/issues/298
//
// The correctness is verified through the sample app of this project for now.
class CreateComponentProcessorTest {

    @Test
    fun `the function must be public`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @MergeComponent(AppScope::class)
            interface ComponentInterface

            @MergeComponent.CreateComponent
            internal expect fun createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory functions for components annotated with `@CreateComponent` must " +
                    "be public.",
            )
        }
    }

    @Test
    fun `the function return type must generate the kotlin-inject component - final component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component                
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @MergeComponent(AppScope::class)
            @Component
            interface ComponentInterface : ComponentInterfaceMerged

            @MergeComponent.CreateComponent
            expect fun createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The return type software.amazon.test.ComponentInterface should not be " +
                    "annotated with `@Component`. In this scenario use the built-in " +
                    "annotations from kotlin-inject itself.",
            )
        }
    }

    @Test
    fun `the function return type must generate the kotlin-inject component - no @MergeComponent annotation`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent

            interface ComponentInterface

            @CreateComponent
            expect fun createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The return type software.amazon.test.ComponentInterface is not annotated " +
                    "with `@MergeComponent`.",
            )
        }
    }

    @Test
    fun `a receiver type is only supported for KClass`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent

            @MergeComponent(AppScope::class)
            interface ComponentInterface

            @CreateComponent
            expect fun String.createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Only a receiver type on KClass<YourComponent> is supported.",
            )
        }
    }

    @Test
    fun `a receiver type is only supported for KClass with the right argument`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent
            import kotlin.reflect.KClass

            @MergeComponent(AppScope::class)
            interface ComponentInterface

            @CreateComponent
            expect fun KClass<String>.createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Only a receiver type on KClass<YourComponent> is supported. " +
                    "The argument was different.",
            )
        }
    }

    @Test
    fun `the number of arguments must match the number of arguments for the component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent
            import kotlin.reflect.KClass

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface(
                @get:Provides val string: String,
            )

            @CreateComponent
            expect fun KClass<ComponentInterface>.createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The number of arguments for the function doesn't match the " +
                    "number of arguments of the component.",
            )
        }
    }

    @Test
    fun `the function must have the expect modifier`() {
        compile(
            """
            package software.amazon.test
                            
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent.CreateComponent
            import kotlin.reflect.KClass

            @MergeComponent(AppScope::class)
            interface ComponentInterface

            @CreateComponent
            fun KClass<ComponentInterface>.createComponent(): ComponentInterface
            """,
            multiplatform = true,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Only expect functions can be annotated with " +
                    "@MergeComponent.CreateComponent. In non-common Kotlin Multiplatform " +
                    "code use the generated `create` extension function on the class " +
                    "object: YourComponent.create(..).",
            )
        }
    }
}
