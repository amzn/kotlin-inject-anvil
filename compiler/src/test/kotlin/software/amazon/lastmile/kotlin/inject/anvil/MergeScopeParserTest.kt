@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class MergeScopeParserTest {

    @Test
    fun `the scope can be parsed from a scope annotation with zero args`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

            @ContributesTo
            @Singleton
            interface ComponentInterface

            @ContributesBinding
            @Singleton
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface").scope()).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
        }
    }

    @Test
    fun `the scope can be parsed from a @ContributesBinding annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

            @ContributesBinding(scope = Singleton::class)
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
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

            @ContributesTo(Singleton::class)
            interface ComponentInterface2
            """,
        ) { resolver ->
            assertThat(
                resolver.clazz("software.amazon.test.ComponentInterface1").scope(),
            ).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
            assertThat(
                resolver.clazz("software.amazon.test.ComponentInterface2").scope(),
            ).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
        }
    }

    @Test
    fun `the scope can be parsed from a @ContributesSubcomponent annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import me.tatarka.inject.annotations.Scope
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            
            @Scope
            annotation class ChildScope

            @ContributesSubcomponent(AppScope::class)
            interface SubcomponentInterface1 {
                @ContributesSubcomponent.Factory(String::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface1
                }          
            }

            @ContributesSubcomponent
            @Singleton
            interface SubcomponentInterface2 {
                @ContributesSubcomponent.Factory
                @ChildScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface2
                }          
            }
            """,
        ) { resolver ->
            assertThat(
                resolver.clazz("software.amazon.test.SubcomponentInterface1").scope(),
            ).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
            assertThat(
                resolver.clazz("software.amazon.test.SubcomponentInterface1.Factory").scope(),
            ).isEqualTo(
                fqName = "kotlin.String",
                annotationFqName = null,
                markerFqName = "kotlin.String",
            )

            assertThat(
                resolver.clazz("software.amazon.test.SubcomponentInterface2").scope(),
            ).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
            assertThat(
                resolver.clazz("software.amazon.test.SubcomponentInterface2.Factory").scope(),
            ).isEqualTo(
                fqName = "software.amazon.test.ChildScope",
                annotationFqName = "software.amazon.test.ChildScope",
                markerFqName = null,
            )
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
            assertThat(
                resolver.clazz("software.amazon.test.ComponentInterface1").scope(),
            ).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )

            assertThat(
                resolver.clazz("software.amazon.test.ComponentInterface2").scope(),
            ).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
        }
    }

    @Test
    fun `the scope can be parsed from a @ContributesBinding annotation without a parameter name`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

            @ContributesBinding(Singleton::class)
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.test.Singleton",
                annotationFqName = "software.amazon.test.Singleton",
                markerFqName = null,
            )
        }
    }

    @Test
    fun `the scope can be parsed from a scope annotation with a marker`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesTo
            @SingleIn(AppScope::class)
            interface ComponentInterface

            @ContributesBinding
            @SingleIn(AppScope::class)
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.ComponentInterface").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = "software.amazon.lastmile.kotlin.inject.anvil.SingleIn",
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )

            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = "software.amazon.lastmile.kotlin.inject.anvil.SingleIn",
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
        }
    }

    @Test
    fun `the scope can be parsed from an annotation without explicit scope annotation`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesBinding(AppScope::class)
            interface Binding1 : CharSequence

            @ContributesBinding(multibinding = true, scope = AppScope::class)
            interface Binding2 : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding1").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
            assertThat(resolver.clazz("software.amazon.test.Binding2").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
        }
    }

    @Test
    fun `the marker scope is used and a different actual scope can be used for kotlin-inject`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

            @ContributesBinding(AppScope::class)
            @Singleton
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
        }
    }

    @Test
    fun `the marker scope is used and a different actual scope can be used for kotlin-inject with a different marker`() {
        compileInPlace(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesBinding(AppScope::class)
            @SingleIn(String::class)
            interface Binding : CharSequence
            """,
        ) { resolver ->
            assertThat(resolver.clazz("software.amazon.test.Binding").scope()).isEqualTo(
                fqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
                annotationFqName = null,
                markerFqName = "software.amazon.lastmile.kotlin.inject.anvil.AppScope",
            )
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
        block: ContextAware.(Resolver) -> Unit,
    ) {
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                symbolProcessorProviders = setOf(symbolProcessorProvider(block)),
            )
            .compile(*sources) {
                assertThat(exitCode).isEqualTo(OK)
            }
    }

    private fun Resolver.clazz(name: String) = requireNotNull(getClassDeclarationByName(name))

    private fun Assert<MergeScope>.isEqualTo(
        fqName: String,
        annotationFqName: String?,
        markerFqName: String?,
    ) {
        all {
            prop(MergeScope::fqName).isEqualTo(fqName)
            prop(MergeScope::annotationFqName).isEqualTo(annotationFqName)
            prop(MergeScope::markerFqName).isEqualTo(markerFqName)
        }
    }
}
