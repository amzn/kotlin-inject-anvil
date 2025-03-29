package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.argumentOfTypeAt
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName
import kotlin.reflect.KClass

/**
 * Generates the code for [ContributesBinding].
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
 */
internal class ContributesBindingProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {

    private val anyFqName = Any::class.requireQualifiedName()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesBinding::class)
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsPublic(it)
                checkHasScope(it)
            }
            .forEach {
                generateComponentInterface(it)
            }

        return emptyList()
    }

    @OptIn(KspExperimental::class)
    @Suppress("LongMethod")
    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val annotations = clazz.findAnnotationsAtLeastOne(ContributesBinding::class)
        checkNoDuplicateBoundTypes(clazz, annotations)
        checkReplacesHasSameScope(clazz, annotations)

        val boundTypes = annotations
            .map {
                GeneratedFunction(
                    boundType = boundType(clazz, it),
                    multibinding = it.argumentOfTypeAt<Boolean>(this, "multibinding") ?: false,
                )
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
                                    val hasAssistedInjection = clazz.getConstructors()
                                        .any { constructor ->
                                            constructor.parameters.any {
                                                it.isAnnotationPresent(Assisted::class)
                                            }
                                        }

                                    if (hasAssistedInjection) {
                                        createAssistedProvider(clazz)
                                    } else {
                                        createNormalProvider(clazz)
                                    }
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

    private fun FunSpec.Builder.createNormalProvider(clazz: KSClassDeclaration) {
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

    @OptIn(KspExperimental::class)
    private fun FunSpec.Builder.createAssistedProvider(clazz: KSClassDeclaration) {
        val constructor = clazz.getConstructors().firstOrNull { constructor ->
            constructor.parameters.any { it.isAnnotationPresent(Assisted::class) }
        } ?: throw IllegalArgumentException(
            "No constructor with @Assisted found in ${clazz.simpleName.asString()}",
        )
        val constructorParameters = constructor.parameters
        val realAssistedFactory: LambdaTypeName = createRealAssistedFactory(
            constructorParameters = constructorParameters,
            clazz = clazz,
        )
        val assistedParameters = constructorParameters
            .filter { it.isAnnotationPresent(Assisted::class) }
        assistedParameters
            .forEach {
                addParameter(
                    ParameterSpec.builder(it.requireName(), it.type.resolve().toTypeName())
                        .addAnnotation(Assisted::class)
                        .build(),
                )
            }
        addParameter(
            ParameterSpec.builder(
                name = "realFactory",
                type = realAssistedFactory,
            ).build(),
        )
        addStatement(
            "return realFactory(${assistedParameters.joinToString { it.requireName() }})",
            clazz.toClassName(),
        )
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
    ): List<KSAnnotation> {
        return findAnnotations(annotation).also {
            check(it.isNotEmpty(), this) {
                "Couldn't find the @${annotation.simpleName} annotation for $this."
            }
        }
    }

    /**
     * Create a lambda to represent the assisted factory provided by kotlin-inject.
     *
     * When marking an injectable class with @Assisted, kotlin-inject will generate a binding in
     * lambda form to inject something that can create an instance.
     */
    @OptIn(KspExperimental::class)
    private fun createRealAssistedFactory(
        constructorParameters: List<KSValueParameter>,
        clazz: KSClassDeclaration,
    ): LambdaTypeName = LambdaTypeName.get(
        parameters = constructorParameters
            .filter { it.isAnnotationPresent(Assisted::class) }
            .map { it.type.toTypeName() }
            .toTypedArray(),
        returnType = clazz.toClassName(),
    )

    private inner class GeneratedFunction(
        boundType: KSType,
        val multibinding: Boolean,
    ) {
        val bindingMethodReturnType by lazy {
            boundType.toClassName()
        }
    }
}
