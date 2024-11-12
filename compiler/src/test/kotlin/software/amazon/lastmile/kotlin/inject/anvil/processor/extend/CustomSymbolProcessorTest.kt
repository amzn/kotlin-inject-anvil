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
import com.squareup.kotlinpoet.AnnotationSpec
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.OPTION_CONTRIBUTING_ANNOTATIONS
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.test.Compilation
import software.amazon.lastmile.kotlin.inject.anvil.test.compile
import software.amazon.lastmile.kotlin.inject.anvil.test.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.test.mergedComponent

private const val CONTRIBUTING_ANNOTATION =
    "software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation"
private const val CONTRIBUTES_RENDERER = "software.amazon.test.ContributesRenderer"

class CustomSymbolProcessorTest {

    @ParameterizedTest(name = "useKspOption = {0}")
    @ValueSource(booleans = [true, false])
    fun `a custom symbol processor can generate code`(useKspOption: Boolean) {
        val markerAnnotation = if (useKspOption) {
            ""
        } else {
            "@$CONTRIBUTING_ANNOTATION"
        }
        val options = if (useKspOption) {
            mapOf(OPTION_CONTRIBUTING_ANNOTATIONS to CONTRIBUTES_RENDERER)
        } else {
            emptyMap()
        }

        // The custom symbol processor will generate a component interface that is contributed
        // to the final component. The generated component interface has a provider method
        // for the type String.
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                processorOptions = options,
                symbolProcessorProviders = setOf(symbolProcessorProvider),
            )
            .compile(
                """
                package software.amazon.test
        
                import me.tatarka.inject.annotations.Component
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
                import kotlin.annotation.AnnotationTarget.CLASS

                $markerAnnotation
                @Target(CLASS)
                annotation class ContributesRenderer
                
                @ContributesRenderer
                class Renderer
    
                @Component
                @MergeComponent(Unit::class)
                @SingleIn(Unit::class)
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

    @ParameterizedTest(name = "useKspOption = {0}")
    @ValueSource(booleans = [true, false])
    fun `a custom symbol processor can generate code with an annotation from a previous compilation`(
        useKspOption: Boolean,
    ) {
        val markerAnnotation = if (useKspOption) {
            ""
        } else {
            "@$CONTRIBUTING_ANNOTATION"
        }
        val options = if (useKspOption) {
            mapOf(OPTION_CONTRIBUTING_ANNOTATIONS to CONTRIBUTES_RENDERER)
        } else {
            emptyMap()
        }

        // This will generate the property and the custom annotation will be picked up by
        // MergeComponentProcessor in the 2nd compilation.
        val previousCompilation = compile(
            """
            package software.amazon.test
        
            import kotlin.annotation.AnnotationTarget.CLASS
                
            $markerAnnotation
            @Target(CLASS)
            annotation class ContributesRenderer
            """,
        )

        // The custom symbol processor will generate a component interface that is contributed
        // to the final component. The generated component interface has a provider method
        // for the type String.
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                processorOptions = options,
                symbolProcessorProviders = setOf(symbolProcessorProvider),
            )
            .addPreviousCompilationResult(previousCompilation)
            .compile(
                """
                package software.amazon.test
        
                import me.tatarka.inject.annotations.Component
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
                
                @ContributesRenderer
                class Renderer
    
                @Component
                @MergeComponent(Unit::class)
                @SingleIn(Unit::class)
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
    fun `custom contribution can be excluded in the merge component`() {
        // The custom symbol processor will generate a component interface that is contributed
        // to the final component. The generated component interface has a provider method
        // for the type String.
        //
        // Notice that we contribute two renderers, which would result in duplicate bindings:
        // e: Error occurred in KSP, check log for detail
        // e: [ksp] /var/folders/rs/q_sbtnln4xzb4h17_tdwq2g00000gr/T/Kotlin-Compilation15827920485744140694/ksp/sources/kotlin/software/amazon/test/Renderer2Component.kt:14: Cannot provide: String
        // e: [ksp] /var/folders/rs/q_sbtnln4xzb4h17_tdwq2g00000gr/T/Kotlin-Compilation15827920485744140694/ksp/sources/kotlin/software/amazon/test/Renderer1Component.kt:14: as it is already provided
        //
        // But since one renderer is excluded the duplicate binding doesn't happen.
        Compilation()
            .configureKotlinInjectAnvilProcessor(
                symbolProcessorProviders = setOf(symbolProcessorProvider),
            )
            .compile(
                """
                package software.amazon.test
        
                import me.tatarka.inject.annotations.Component
                import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
                import kotlin.annotation.AnnotationTarget.CLASS

                @ContributingAnnotation
                annotation class ContributesRenderer
                
                @ContributesRenderer
                class Renderer1
                
                @ContributesRenderer
                class Renderer2
    
                @Component
                @MergeComponent(Unit::class, exclude = [Renderer2::class])
                @SingleIn(Unit::class)
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
                            val key = clazz.simpleName.asString()
                            val componentClassName = ClassName(
                                "software.amazon.test",
                                "${key}Component",
                            )
                            val fileSpec = FileSpec.builder(componentClassName)
                                .addType(
                                    TypeSpec
                                        .interfaceBuilder(componentClassName)
                                        .addOriginatingKSFile(clazz.containingFile!!)
                                        .addOriginAnnotation(clazz)
                                        .addAnnotation(
                                            AnnotationSpec.builder(ContributesTo::class)
                                                .addMember("Unit::class")
                                                .build(),
                                        )
                                        .addAnnotation(
                                            AnnotationSpec.builder(SingleIn::class)
                                                .addMember("Unit::class")
                                                .build(),
                                        )
                                        .addFunction(
                                            FunSpec
                                                .builder("provideString$key")
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
