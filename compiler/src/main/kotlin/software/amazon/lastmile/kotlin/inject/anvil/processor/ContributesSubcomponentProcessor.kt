package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.internal.Subcomponent

/**
 * Generates the code for [ContributesSubcomponent].
 *
 * This processor runs when a component annotated with [MergeComponent] is being processed. In the
 * [LOOKUP_PACKAGE] it finds all contributed component interfaces that are annotated with
 * [Subcomponent] and where the scope matches the one of the component annotated with
 * [MergeComponent]. In other words, these are all [ContributesSubcomponent.Factory] interfaces
 * with the same parent scope as the [MergeComponent] interface.
 *
 * For each factory and contributed subcomponent it then will generate the final component and
 * factory, e.g.
 * ```
 * package software.amazon.test
 *
 * @ContributesSubcomponent
 * @ChildScope
 * interface Subcomponent {
 *     @ContributesSubcomponent.Factory
 *     @ParentScope
 *     interface Factory {
 *         fun createSubcomponent(): Subcomponent
 *     }
 * }
 *
 * // The trigger for generating the code:
 * @Component
 * @MergeComponent
 * @ParentScope
 * abstract class ParentComponent : ParentComponentMerged
 * ```
 * Will generate:
 * ```
 * package software.amazon.test
 *
 * @Component
 * @MergeComponent
 * @ChildScope
 * abstract class SubcomponentFinal(
 *     @Component val parentComponent: ParentComponent,
 * ) : Subcomponent, SubcomponentMerged {
 *     interface Factory : Subcomponent.Factory {
 *         override fun createSubcomponent(): Subcomponent {
 *             return SubcomponentFinal.create(this as ParentComponent)
 *         }
 *     }
 * }
 *
 * interface ParentComponentMerged : SubcomponentFinal.Factory, ...
 * ```
 * Note that `ParentComponentMerged` extends `SubcomponentFinal.Factory`.
 */
@OptIn(KspExperimental::class)
internal class ContributesSubcomponentProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : ContextAware {

    fun generateFinalComponents(
        parentScopeComponent: KSClassDeclaration,
        componentInterfaces: List<KSClassDeclaration>,
    ): List<ClassName> {
        return componentInterfaces
            .filter { it.isAnnotationPresent(Subcomponent::class) }
            .map { generatedFactoryInterface ->
                // The returns the original factory interface and not the generated one in the
                // LOOKUP_PACKAGE.
                val originalFactoryInterface = generatedFactoryInterface.origin()
                check(
                    originalFactoryInterface
                        .isAnnotationPresent(ContributesSubcomponent.Factory::class),
                    generatedFactoryInterface,
                ) {
                    "Couldn't find @ContributesSubcomponent.Factory for " +
                        "${generatedFactoryInterface.simpleName.asString()}."
                }

                generateFinalComponent(
                    parentScopeComponent = parentScopeComponent,
                    subcomponent = generatedFactoryInterface.contributedSubcomponent(),
                    factoryInterface = originalFactoryInterface,
                    generatedFactoryInterface = generatedFactoryInterface,
                )
            }
    }

    @Suppress("LongMethod")
    private fun generateFinalComponent(
        parentScopeComponent: KSClassDeclaration,
        subcomponent: KSClassDeclaration,
        factoryInterface: KSClassDeclaration,
        generatedFactoryInterface: KSClassDeclaration,
    ): ClassName {
        val scope = subcomponent.scope()
        val function = factoryInterface.factoryFunctions().single()

        val parameters = function.parameters.map {
            requireNotNull(it.name?.asString()) to it.type.toTypeName()
        }

        val finalComponentClassName = ClassName(
            subcomponent.packageName.asString(),
            "${subcomponent.innerClassNames()}Final${parentScopeComponent.innerClassNames()}",
        )

        val fileSpec = FileSpec.builder(finalComponentClassName)
            .addType(
                TypeSpec
                    .classBuilder(finalComponentClassName)
                    .addAnnotation(Component::class)
                    .addAnnotation(MergeComponent::class)
                    .addAnnotation(scope.toAnnotationSpec())
                    .addOriginAnnotation(subcomponent)
                    .addModifiers(KModifier.ABSTRACT)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(
                                ParameterSpec
                                    .builder(
                                        "parentComponent",
                                        parentScopeComponent.toClassName(),
                                    )
                                    .addAnnotation(Component::class)
                                    .build(),
                            )
                            .addParameters(
                                parameters.map { (name, type) ->
                                    ParameterSpec
                                        .builder(name, type)
                                        .addAnnotation(
                                            AnnotationSpec.builder(Provides::class)
                                                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                                                .build(),
                                        )
                                        .build()
                                },
                            )
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("parentComponent", parentScopeComponent.toClassName())
                            .initializer("parentComponent")
                            .build(),
                    )
                    .addProperties(
                        parameters.map { (name, type) ->
                            PropertySpec.builder(name, type)
                                .initializer(name)
                                .build()
                        },
                    )
                    .addSuperinterface(subcomponent.toClassName())
                    .addSuperinterface(
                        finalComponentClassName.peerClass(
                            finalComponentClassName.simpleName + "Merged",
                        ),
                    )
                    .addType(
                        TypeSpec
                            .interfaceBuilder("Factory")
                            .addSuperinterface(factoryInterface.toClassName())
                            .addFunction(
                                FunSpec.builder(function.simpleName.asString())
                                    .addModifiers(KModifier.OVERRIDE)
                                    .addParameters(
                                        parameters.map { (name, type) ->
                                            ParameterSpec
                                                .builder(name, type)
                                                .build()
                                        },
                                    )
                                    .returns(subcomponent.toClassName())
                                    .apply {
                                        val paramTemplates = if (parameters.isEmpty()) {
                                            ""
                                        } else {
                                            parameters.joinToString(
                                                separator = ", ",
                                                prefix = ", ",
                                            ) {
                                                it.first
                                            }
                                        }

                                        addStatement(
                                            "return %T::class.create(this as %T$paramTemplates)",
                                            finalComponentClassName,
                                            parentScopeComponent.toClassName(),
                                        )
                                    }
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            aggregating = true,
            originatingKSFiles = buildSet {
                add(parentScopeComponent.requireContainingFile())
                subcomponent.containingFile?.let { add(it) }
                factoryInterface.containingFile?.let { add(it) }
                generatedFactoryInterface.containingFile?.let { add(it) }
            },
        )

        return finalComponentClassName.nestedClass("Factory")
    }
}
