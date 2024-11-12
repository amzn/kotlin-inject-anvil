package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
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
 * @ContributesSubcomponent(LoggedInScope::class)
 * @SingleIn(AppScope::class)
 * interface Subcomponent {
 *     @ContributesSubcomponent.Factory(AppScope::class)
 *     interface Factory {
 *         fun createSubcomponent(): Subcomponent
 *     }
 * }
 *
 * // The trigger for generating the code:
 * @Component
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class ParentComponent : ParentComponentMerged
 * ```
 * Will generate:
 * ```
 * package software.amazon.test
 *
 * @Component
 * @MergeComponent(LoggedInScope::class)
 * @SingleIn(AppScope::class)
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
        val kotlinInjectScope = requireKotlinInjectScope(subcomponent)

        val function = factoryInterface.factoryFunctions().single()

        val parameters = function.parameters.map { valueParameter ->
            FactoryParameter(
                name = requireNotNull(valueParameter.name?.asString()),
                typeName = valueParameter.type.toTypeName(),
                qualifier = valueParameter.annotations
                    .filter { it.isKotlinInjectQualifierAnnotation() }
                    .singleOrNull(),
                isOverride = hasMatchingProperty(valueParameter, subcomponent),
            )
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
                    .addAnnotation(
                        AnnotationSpec
                            .builder(MergeComponent::class)
                            .addMember(
                                "scope = %T::class",
                                subcomponent.scope().type.toClassName(),
                            )
                            .build(),
                    )
                    .addAnnotation(kotlinInjectScope.toAnnotationSpec())
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
                                parameters.map { parameter ->
                                    ParameterSpec
                                        .builder(parameter.name, parameter.typeName)
                                        .addAnnotation(
                                            AnnotationSpec.builder(Provides::class)
                                                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                                                .build(),
                                        )
                                        .apply {
                                            if (parameter.qualifier != null) {
                                                addAnnotation(
                                                    parameter.qualifier
                                                        .toAnnotationSpec()
                                                        .toBuilder()
                                                        .useSiteTarget(
                                                            AnnotationSpec.UseSiteTarget.GET,
                                                        )
                                                        .build(),
                                                )
                                            }
                                        }
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
                        parameters.map { parameter ->
                            PropertySpec.builder(parameter.name, parameter.typeName)
                                .initializer(parameter.name)
                                .apply {
                                    if (parameter.isOverride) {
                                        addModifiers(KModifier.OVERRIDE)
                                    }
                                }
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
                                        parameters.map { parameter ->
                                            ParameterSpec
                                                .builder(parameter.name, parameter.typeName)
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
                                                it.name
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

    private data class FactoryParameter(
        val name: String,
        val typeName: TypeName,
        val qualifier: KSAnnotation?,
        val isOverride: Boolean,
    )
}

private fun hasMatchingProperty(
    parameter: KSValueParameter,
    classDeclaration: KSClassDeclaration,
): Boolean {
    val parameterName = parameter.name?.asString()
    val parameterType = parameter.type.resolve()

    // Function to check properties in a class declaration
    fun checkPropertiesInClass(klass: KSClassDeclaration): Boolean {
        return klass.getAllProperties().any { property ->
            property.simpleName.asString() == parameterName &&
                property.type.resolve() == parameterType
        }
    }

    // Check the current class and its ancestors
    var currentClass: KSClassDeclaration? = classDeclaration
    while (currentClass != null) {
        if (checkPropertiesInClass(currentClass)) {
            return true
        }
        currentClass = currentClass.superTypes
            .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
            .firstOrNull()
    }

    return false
}
