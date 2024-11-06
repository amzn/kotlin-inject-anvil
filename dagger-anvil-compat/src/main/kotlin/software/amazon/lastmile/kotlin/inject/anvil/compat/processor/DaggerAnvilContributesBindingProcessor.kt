package software.amazon.lastmile.kotlin.inject.anvil.compat.processor

import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.compat.OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS
import software.amazon.lastmile.kotlin.inject.anvil.compat.createUnsupportedParamMessage
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName
import kotlin.reflect.KClass

/**
 * Generates the code for [com.squareup.anvil.annotations.ContributesBinding] and
 * [com.squareup.anvil.annotations.ContributesMultibinding].
 *
 * In the lookup package [LOOKUP_PACKAGE] a new interface is generated with a provider method for
 * the annotated type. To avoid name clashes the package name of the original interface is encoded
 * in the interface name. E.g.
 * ```
 * package software.amazon.test
 *
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class)
 * class RealAuthenticator : Authenticator
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @Origin(RealAuthenticator::class)
 * interface SoftwareAmazonTestRealAuthenticator {
 *     @Provides fun provideRealAuthenticatorAuthenticator(
 *         realAuthenticator: RealAuthenticator
 *     ): Authenticator = realAuthenticator
 * }
 * ```
 *
 * When using [com.squareup.anvil.annotations.ContributesMultibinding], the generated provider will
 * be annotated with `@IntoSet`.
 */
internal class DaggerAnvilContributesBindingProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor, ContextAware {

    private val anyFqName = Any::class.requireQualifiedName()

    private val ignoreUnsupportedParameters: Boolean by lazy {
        options[OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS] == "true"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotations(
            ContributesBinding::class,
            ContributesMultibinding::class,
        )
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsPublic(it)
                checkHasScope(it)
                if (!ignoreUnsupportedParameters) {
                    warnIfUnsupportedParameters(it)
                }
            }
            .forEach {
                generateComponentInterface(it)
            }
        return emptyList()
    }

    @Suppress("LongMethod")
    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val annotations = clazz.findAnnotationsAtLeastOne(
            ContributesBinding::class,
            ContributesMultibinding::class,
        )
        checkNoDuplicateBoundTypes(clazz, annotations)

        val typeNames = annotations.associateWith { it.annotationType.resolve().toTypeName() }

        val boundTypes = annotations
            .map { annotation ->
                when (val typeName = typeNames[annotation]) {
                    ContributesBinding::class.asTypeName() -> {
                        GeneratedFunction(
                            boundType = boundType(clazz, annotation),
                            multibinding = false,
                        )
                    }

                    ContributesMultibinding::class.asTypeName() -> {
                        GeneratedFunction(
                            boundType = boundType(clazz, annotation),
                            multibinding = true,
                        )
                    }

                    else -> error("Unknown annotation type: $typeName")
                }
            }
            .distinctBy { it.bindingMethodReturnType.canonicalName + it.multibinding }

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addFunctions(
                        boundTypes.map { function ->
                            val multibindingSuffix = if (function.multibinding) {
                                "Multibinding"
                            } else {
                                ""
                            }
                            FunSpec
                                .builder(
                                    "provide${clazz.innerClassNames()}" +
                                        function.bindingMethodReturnType.simpleName +
                                        multibindingSuffix,
                                )
                                .addAnnotation(Provides::class)
                                .apply {
                                    if (function.multibinding) {
                                        addAnnotation(IntoSet::class)
                                    }
                                }
                                .apply {
                                    val parameterName = clazz.innerClassNames().decapitalize()
                                    addParameter(
                                        ParameterSpec
                                            .builder(
                                                name = parameterName,
                                                type = clazz.toClassName(),
                                            )
                                            .build(),
                                    )

                                    addStatement("return $parameterName")
                                }
                                .returns(function.bindingMethodReturnType)
                                .build()
                        },
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun warnIfUnsupportedParameters(clazz: KSClassDeclaration) {
        // Warn about @ContributesBinding unsupported parameters.
        clazz.findAnnotationOrNull(ContributesBinding::class)
            ?.let { annotation ->
                annotation
                    .arguments
                    .filter { it.name?.asString() in contributesBindingUnsupportedParameters }
                    .filterNot { it.isDefault() }
                    .forEach { argument ->
                        val argumentName = requireNotNull(argument.name).asString()
                        logger.warn(
                            createUnsupportedParamMessage("ContributesBinding", argumentName),
                            argument,
                        )
                    }
            }
        // Warn about @ContributesMultibinding unsupported parameters.
        clazz.findAnnotationOrNull(ContributesMultibinding::class)
            ?.let { annotation ->
                annotation
                    .arguments
                    .filter { it.name?.asString() in contributesMultibindingUnsupportedParameters }
                    .filterNot { it.isDefault() }
                    .forEach { argument ->
                        val argumentName = requireNotNull(argument.name).asString()
                        logger.warn(
                            createUnsupportedParamMessage("ContributesMultibinding", argumentName),
                            argument,
                        )
                    }
            }
    }

    private fun checkNoDuplicateBoundTypes(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ) {
        annotations
            .mapNotNull { boundTypeFromAnnotation(it) }
            .map { it.declaration.requireQualifiedName() }
            .takeIf { it.isNotEmpty() }
            ?.reduce { previous, next ->
                check(previous != next, clazz) {
                    "The same type should not be contributed twice: $next."
                }

                previous
            }
    }

    private fun boundTypeFromAnnotation(annotation: KSAnnotation): KSType? {
        return annotation.arguments.firstOrNull { it.name?.asString() == "boundType" }
            ?.let { it.value as? KSType }
            ?.takeIf {
                it.declaration.requireQualifiedName() != Unit::class.requireQualifiedName()
            }
    }

    @Suppress("ReturnCount")
    private fun boundType(
        clazz: KSClassDeclaration,
        annotation: KSAnnotation,
    ): KSType {
        boundTypeFromAnnotation(annotation)?.let { return it }

        // The bound type is not defined in the annotation, let's inspect the super types.
        val superTypes = clazz.superTypes
            .map { it.resolve() }
            .filter { it.declaration.requireQualifiedName() != anyFqName }
            .toList()

        when (superTypes.size) {
            0 -> {
                val message = "The bound type could not be determined for " +
                    "${clazz.simpleName.asString()}. There are no super types."
                logger.error(message, clazz)
                throw IllegalArgumentException(message)
            }

            1 -> {
                return superTypes.single()
            }

            else -> {
                val message = "The bound type could not be determined for " +
                    "${clazz.simpleName.asString()}. There are multiple super types: " +
                    superTypes.joinToString { it.declaration.simpleName.asString() } +
                    "."
                logger.error(message, clazz)
                throw IllegalArgumentException(message)
            }
        }
    }

    private fun KSClassDeclaration.findAnnotationsAtLeastOne(
        annotation: KClass<out Annotation>,
        vararg annotations: KClass<out Annotation>,
    ): List<KSAnnotation> {
        return buildList {
            add(annotation)
            addAll(annotations)
        }
            .flatMap { findAnnotations(it) }
            .also {
                check(it.isNotEmpty(), this) {
                    "Couldn't find the @${annotation.simpleName} annotation for $this."
                }
            }
    }

    private inner class GeneratedFunction(
        boundType: KSType,
        val multibinding: Boolean,
    ) {
        val bindingMethodReturnType by lazy {
            boundType.toClassName()
        }
    }

    private companion object {
        val contributesBindingUnsupportedParameters = setOf(
            "replaces",
            "priority",
            "ignoreQualifier",
            "rank",
        )
        val contributesMultibindingUnsupportedParameters = setOf(
            "replaces",
            "ignoreQualifier",
        )
    }
}
