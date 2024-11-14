package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.MapKeyAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.argumentOfTypeAt
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize
import software.amazon.lastmile.kotlin.inject.anvil.pairTypeOf
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

    @Suppress("LongMethod")
    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val annotations = clazz.findAnnotationsAtLeastOne(ContributesBinding::class)
        checkNoDuplicateBoundTypes(clazz, annotations)

        val mapKeys = clazz.mapKeys()

        val boundTypes = annotations
            .map {
                val boundType = boundType(clazz, it)
                val multibinding = it.argumentOfTypeAt<Boolean>(this, "multibinding") ?: false
                if (multibinding && mapKeys.isNotEmpty()) {
                    mapKeys.map { mapKey ->
                        GeneratedFunction(
                            boundType = boundType,
                            multibinding = true,
                            mapKey = mapKey,
                        )
                    }
                } else {
                    listOf(
                        GeneratedFunction(
                            boundType = boundType,
                            multibinding = multibinding,
                        ),
                    )
                }
            }
            .flatten()
            .distinctBy { it.bindingMethodReturnType.canonicalName + it.multibinding + it.mapKey }

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addFunctions(
                        boundTypes.map { function ->
                            FunSpec
                                .builder(
                                    "provide${clazz.innerClassNames()}" +
                                        function.bindingMethodReturnType.simpleName +
                                        function.multiBindingSuffix,
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

                                    when {
                                        function.multibinding && function.mapKey != null -> {
                                            addAnnotation(IntoMap::class)
                                            val (format, value) = function.mapKey.value()
                                            addStatement("return $format to $parameterName", value)
                                            returns(
                                                pairTypeOf(
                                                    function.mapKey.type(),
                                                    function.bindingMethodReturnType,
                                                ),
                                            )
                                        }

                                        function.multibinding -> {
                                            addAnnotation(IntoSet::class)
                                            addStatement("return $parameterName")
                                            returns(function.bindingMethodReturnType)
                                        }

                                        else -> {
                                            addStatement("return $parameterName")
                                            returns(function.bindingMethodReturnType)
                                        }
                                    }
                                }
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

    private inner class GeneratedFunction(
        boundType: KSType,
        val multibinding: Boolean,
        val mapKey: MapKeyAnnotation? = null,
    ) {
        val bindingMethodReturnType by lazy {
            boundType.toClassName()
        }

        val multiBindingSuffix = if (multibinding) {
            val mapKeySuffix = mapKey
                ?.multiBindingSuffix()
                ?.let { "_$it" } ?: ""
            "Multibinding$mapKeySuffix"
        } else {
            ""
        }
    }

    private fun MapKeyAnnotation.type(): TypeName {
        val type = when (val value = argument.value) {
            is Byte -> Byte::class.asTypeName()
            is Short -> Short::class.asTypeName()
            is Int -> Int::class.asTypeName()
            is Long -> Long::class.asTypeName()
            is Float -> Float::class.asTypeName()
            is Double -> Double::class.asTypeName()
            is Char -> Char::class.asTypeName()
            is String -> String::class.asTypeName()
            is Boolean -> Boolean::class.asTypeName()

            is KSType -> KClass::class.asTypeName().parameterizedBy(STAR)
            is KSClassDeclaration -> when (value.classKind) {
                ClassKind.ENUM_CLASS -> value.toClassName()
                ClassKind.ENUM_ENTRY -> (value.parent as? KSClassDeclaration)?.toClassName()
                else -> null
            }

            else -> null
        }
        return requireNotNull(type, argument) {
            "The argument type could not be determined for " +
                "${argument.name?.asString()} = ${argument.value}."
        }
    }

    private fun MapKeyAnnotation.value(): Pair<String, Any> {
        val value = argument.value

        val format = when (value) {
            is Byte,
            is Short,
            is Int,
            is Long,
            is Float,
            is Double,
            is Boolean,
            -> "%L"

            is Char -> "'%L'"
            is String -> "%S"

            is KSType -> "%T::class"
            is KSClassDeclaration -> "%L"

            else -> {
                val message = "The argument value could not be determined for " +
                    "${argument.name?.asString()} = ${argument.value}."
                logger.error(message, argument)
                throw IllegalArgumentException(message)
            }
        }

        val argValue = when (value) {
            is Byte -> "$value.toByte()"
            is Short -> "$value.toShort()"
            is Float -> "$value.toFloat()"

            is Int,
            is Long,
            is Double,
            is Boolean,
            is Char,
            is String,
            -> value

            is KSType -> value.toTypeName()
            is KSClassDeclaration -> value

            else -> {
                val message = "The argument value could not be determined for " +
                    "${argument.name?.asString()} = ${argument.value}."
                logger.error(message, argument)
                throw IllegalArgumentException(message)
            }
        }

        return format to argValue
    }

    private fun MapKeyAnnotation.multiBindingSuffix(): String {
        return when (val value = argument.value) {
            is Byte -> "${value}b"
            is Short -> "${value}s"

            is Int,
            is Long,
            is Char,
            is String,
            is Boolean,
            -> value.toString()

            is Float,
            is Double,
            -> value.toString().replace(".", "_")

            is KSType -> value.declaration.simpleName.asString()
            is KSClassDeclaration -> value.safeRequiredQualifiedName

            else -> {
                val message = "The argument value could not be determined for " +
                    "${argument.name?.asString()} = ${argument.value}."
                logger.error(message, argument)
                throw IllegalArgumentException(message)
            }
        }
    }

    private val KSDeclaration.safeRequiredQualifiedName: String
        get() = qualifiedName!!.asString().replace(".", "_")
}
