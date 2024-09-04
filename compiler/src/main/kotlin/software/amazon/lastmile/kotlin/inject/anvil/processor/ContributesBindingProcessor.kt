package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize

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
 * @SingleInAppScope
 * @ContributesBinding
 * class RealAuthenticator : Authenticator
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @SingleInAppScope
 * @Origin(ComponentInterface::class)
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
            }
            .forEach {
                generateComponentInterface(it)
            }

        return emptyList()
    }

    @Suppress("LongMethod")
    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val annotations = clazz.findAnnotations(ContributesBinding::class)
        checkNoDuplicateBoundTypes(clazz, annotations)

        val scope = scope(clazz, annotations)

        val boundTypes = annotations
            .map {
                GeneratedFunction(
                    boundType = boundType(clazz, it),
                )
            }
            .distinctBy { it.bindingMethodReturnType.canonicalName }

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addAnnotation(scope.toClassName())
                    .addOriginAnnotation(clazz)
                    .addFunctions(
                        boundTypes.map { function ->
                            FunSpec
                                .builder(
                                    "provide${clazz.innerClassNames()}" +
                                        function.bindingMethodReturnType.simpleName,
                                )
                                .addAnnotation(Provides::class)
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

    private fun scope(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ): KSType {
        val explicitScopes = annotations.mapNotNull { annotation ->
            annotation.arguments.firstOrNull { it.name?.asString() == "scope" }
                ?.let { it.value as? KSType }
                ?.takeIf {
                    it.declaration.requireQualifiedName() !=
                        Annotation::class.requireQualifiedName()
                }
        }

        val classScope = clazz.scopeOrNull()?.annotationType?.resolve()

        if (explicitScopes.isNotEmpty()) {
            check(explicitScopes.size == annotations.size, clazz) {
                "If one @ContributesBinding annotation has an explicit scope, then all " +
                    "annotations must specify an explicit scope."
            }

            explicitScopes.scan(
                explicitScopes.first().declaration.requireQualifiedName(),
            ) { previous, next ->
                check(previous == next.declaration.requireQualifiedName(), clazz) {
                    "All explicit scopes on @ContributesBinding annotations must be the same."
                }
                previous
            }

            val explicitScope = explicitScopes.first()

            if (classScope != null) {
                check(
                    classScope.declaration.requireQualifiedName() ==
                        explicitScope.declaration.requireQualifiedName(),
                    clazz,
                ) {
                    "A scope was defined explicitly on the @ContributesBinding annotation " +
                        "`${explicitScope.declaration.requireQualifiedName()}` and the class " +
                        "itself is scoped using " +
                        "`${classScope.declaration.requireQualifiedName()}`. It's not allowed " +
                        "to mix different scopes."
                }
            }

            check(classScope == null, clazz) {
                "A scope was defined explicitly on the @ContributesBinding annotation " +
                    "`${explicitScope.declaration.requireQualifiedName()}` and the class itself " +
                    "is scoped using `${classScope!!.declaration.requireQualifiedName()}`. In " +
                    "this case the explicit scope on the @ContributesBinding annotation can be " +
                    "removed."
            }

            return explicitScope
        }

        return requireNotNull(classScope, clazz) {
            "Couldn't find scope for ${clazz.simpleName.asString()}. For unscoped " +
                "objects it is required to specify the target scope on the @ContributesBinding " +
                "annotation."
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

    private inner class GeneratedFunction(
        boundType: KSType,
    ) {
        val bindingMethodReturnType by lazy {
            boundType.toClassName()
        }
    }
}
