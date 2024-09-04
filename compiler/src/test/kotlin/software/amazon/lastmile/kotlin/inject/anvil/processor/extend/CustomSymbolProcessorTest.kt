@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor.extend

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import me.tatarka.inject.annotations.Provides
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.Compilation
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.mergedComponent
import software.amazon.test.SingleInAppScope

class CustomSymbolProcessorTest {

    @Test
    fun `a custom symbol processor can generate code`() {
        // The custom symbol processor will generate a component interface that is contributed
        // to the final component. The generated component interface has a provider method
        // for the type String.
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                symbolProcessorProviders = setOf(symbolProcessorProvider),
            )
            .compile(
                """
                package software.amazon.test
        
                import me.tatarka.inject.annotations.Component
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
                import kotlin.annotation.AnnotationTarget.CLASS

                @ContributingAnnotation
                @Target(CLASS)
                annotation class ContributesRenderer
                
                @ContributesRenderer
                class Renderer
    
                @Component
                @MergeComponent
                @SingleInAppScope
                interface ComponentInterface : ComponentInterfaceMerged {
                    val string: String
                }
                """,
            )
            .run {
                assertThat(exitCode).isEqualTo(OK)
                assertThat(componentInterface.mergedComponent).isNotNull()
            }
    }

    @Test
    fun `a custom symbol processor can generate code with an annotation from a previous compilation`() {
        // This will generate the property and the custom annotation will be picked up by
        // MergeComponentProcessor in the 2nd compilation.
        val previousCompilation = compile(
            """
            package software.amazon.test
        
            import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
            import kotlin.annotation.AnnotationTarget.CLASS
                
            @ContributingAnnotation
            @Target(CLASS)
            annotation class ContributesRenderer
            """,
        )

        // The custom symbol processor will generate a component interface that is contributed
        // to the final component. The generated component interface has a provider method
        // for the type String.
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                symbolProcessorProviders = setOf(symbolProcessorProvider),
            )
            .addPreviousCompilationResult(previousCompilation)
            .compile(
                """
                package software.amazon.test
        
                import me.tatarka.inject.annotations.Component
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                
                @ContributesRenderer
                class Renderer
    
                @Component
                @MergeComponent
                @SingleInAppScope
                interface ComponentInterface : ComponentInterfaceMerged {
                    val string: String
                }
                """,
            )
            .run {
                assertThat(exitCode).isEqualTo(OK)
                assertThat(componentInterface.mergedComponent).isNotNull()
            }
    }

    private val symbolProcessorProvider = object : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    resolver
                        .getSymbolsWithAnnotation("software.amazon.test.ContributesRenderer")
                        .filterIsInstance<KSClassDeclaration>()
                        .forEach { clazz ->
                            val componentClassName = ClassName(
                                "software.amazon.test",
                                "RendererComponent",
                            )
                            val fileSpec = FileSpec.builder(componentClassName)
                                .addType(
                                    TypeSpec
                                        .interfaceBuilder(componentClassName)
                                        .addOriginatingKSFile(clazz.containingFile!!)
                                        .addAnnotation(ContributesTo::class)
                                        .addAnnotation(SingleInAppScope::class)
                                        .addFunction(
                                            FunSpec
                                                .builder("provideString")
                                                .addAnnotation(Provides::class)
                                                .returns(String::class)
                                                .addCode("return \"renderer\"")
                                                .build(),
                                        )
                                        .build(),
                                )
                                .build()

                            fileSpec.writeTo(environment.codeGenerator, aggregating = false)
                        }

                    return emptyList()
                }
            }
        }
    }
}
