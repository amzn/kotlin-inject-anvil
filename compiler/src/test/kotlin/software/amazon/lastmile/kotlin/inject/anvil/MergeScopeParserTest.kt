@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil

import assertk.Assert
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.test.Compilation
import software.amazon.lastmile.kotlin.inject.anvil.test.isError
import software.amazon.lastmile.kotlin.inject.anvil.test.isOk

class MergeScopeParserTest {

    @Test
    fun `the scope can be parsed from a @ContributesBinding annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

            @ContributesBinding(scope = Unit::class)
            interface Binding1 : CharSequence

            @ContributesBinding(String::class)
            interface Binding2 : CharSequence

            @ContributesBinding(multibinding = true, scope = CharSequence::class)
            interface Binding3 : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding1").scope())
                .isEqualTo("kotlin.Unit")
            assertThat(resolver.clazz("software.amazon.test.Binding2").scope())
                .isEqualTo("kotlin.String")
            assertThat(resolver.clazz("software.amazon.test.Binding3").scope())
                .isEqualTo("kotlin.CharSequence")
        }
    }

    @Test
    fun `the scope can be parsed from a @ContributesTo annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            @ContributesTo(AppScope::class)
            interface ComponentInterface1

            @ContributesTo(scope = Unit::class)
            interface ComponentInterface2
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface1").scope())
                .isEqualTo("software.amazon.lastmile.kotlin.inject.anvil.AppScope")
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface2").scope())
                .isEqualTo("kotlin.Unit")
        }
    }

    @Test
    fun `the scope can be parsed from a @ContributesSubcomponent annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            
            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.SubcomponentInterface").scope())
                .isEqualTo("kotlin.String")
            assertThat(
                resolver.clazz("software.amazon.test.SubcomponentInterface.Factory").scope(),
            ).isEqualTo("software.amazon.lastmile.kotlin.inject.anvil.AppScope")
        }
    }

    @Test
    fun `the scope can be parsed from a @MergeComponent annotation`() {
        compileInPlace(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @Component
            @MergeComponent(AppScope::class)
            abstract class ComponentInterface1 : ComponentInterface1Merged

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface2 : ComponentInterface2Merged
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface1").scope())
                .isEqualTo("software.amazon.lastmile.kotlin.inject.anvil.AppScope")
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface2").scope())
                .isEqualTo("software.amazon.lastmile.kotlin.inject.anvil.AppScope")
        }
    }

    @Test
    fun `the scope can be different for kotlin-inject`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import me.tatarka.inject.annotations.Scope
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            
            @Scope
            annotation class Singleton

            @ContributesBinding(AppScope::class)
            @Singleton
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding").scope())
                .isEqualTo("software.amazon.lastmile.kotlin.inject.anvil.AppScope")
        }
    }

    @Test
    fun `using two different scope parameters is forbidden`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Interface1
            interface Interface2

            @ContributesBinding(AppScope::class)
            @SingleIn(String::class)
            interface Binding1 : Interface1

            @ContributesBinding(AppScope::class, boundType = Interface1::class)
            @ContributesBinding(String::class, boundType = Interface2::class)
            interface Binding2 : Interface1, Interface2
            """,
            exitCode = COMPILATION_ERROR,
        ) { resolver ->
            assertFailure {
                resolver.clazz("software.amazon.test.Binding1").scope()
            }.messageContains("All scopes on annotations must be the same.")

            assertFailure {
                resolver.clazz("software.amazon.test.Binding2").scope()
            }.messageContains("All scopes on annotations must be the same.")
        }
    }

    private fun symbolProcessorProvider(
        block: ContextAware.(Resolver) -> Unit,
    ): SymbolProcessorProvider = object : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor, ContextAware {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    block(this, resolver)
                    return emptyList()
                }

                override val logger: KSPLogger = environment.logger
            }
        }
    }

    private fun compileInPlace(
        @Language("kotlin") vararg sources: String,
        exitCode: KotlinCompilation.ExitCode = OK,
        block: ContextAware.(Resolver) -> Unit,
    ) {
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                symbolProcessorProviders = setOf(symbolProcessorProvider(block)),
            )
            .compile(*sources) {
                if (exitCode == OK) {
                    assertThat(exitCode).isOk()
                } else {
                    assertThat(exitCode).isError()
                }
            }
    }

    private fun Resolver.clazz(name: String) = requireNotNull(getClassDeclarationByName(name))

    private fun Assert<MergeScope>.isEqualTo(scopeFqName: String) {
        transform {
            requireNotNull(it.type.declaration.qualifiedName).asString()
        }.isEqualTo(scopeFqName)
    }
}
