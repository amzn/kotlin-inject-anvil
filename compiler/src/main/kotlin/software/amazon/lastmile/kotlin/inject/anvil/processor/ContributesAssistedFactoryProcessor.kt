@file:OptIn(KspExperimental::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName
import kotlin.reflect.KClass

internal class ContributesAssistedFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {

    private val anyFqName = Any::class.requireQualifiedName()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesAssistedFactory::class)
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

        val annotations = clazz.findAnnotationsAtLeastOne(ContributesAssistedFactory::class)
        checkNoDuplicateBoundTypes(clazz, annotations)

        val assistedFactoryType = assistedFactoryFromAnnotation(annotations.first())
        checkIsSingleMethodInterface(assistedFactoryType)

        val generatedFunction = annotations.first().let {
            GeneratedFunction(
                boundType = getSingleMethodReturnType(assistedFactoryType)!!,
                assistedFactory = assistedFactoryFromAnnotation(it),
            )
        }

        val fileSpec = FileSpec.builder(componentClassName)
            .apply {
                addImport(
                    generatedFunction.bindingMethodReturnType.packageName,
                    generatedFunction.bindingMethodReturnType.simpleName,
                )
                addImport(
                    generatedFunction.assistedFactoryReturnType.packageName,
                    generatedFunction.assistedFactoryReturnType.simpleNames.joinToString("."),
                )
            }
            .addType(
                createComponent(
                    componentClassName = componentClassName,
                    clazz = clazz,
                    function = generatedFunction,
                    realAssistedFactory = realAssistedFactory,
                    constructorParameters = constructorParameters,
                ),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun createComponent(
        componentClassName: ClassName,
        clazz: KSClassDeclaration,
        function: GeneratedFunction,
        realAssistedFactory: LambdaTypeName,
        constructorParameters: List<KSValueParameter>,
    ): TypeSpec = TypeSpec
        .interfaceBuilder(componentClassName)
        .addOriginatingKSFile(clazz.requireContainingFile())
        .addOriginAnnotation(clazz)
        .addFunction(
            FunSpec
                .builder(
                    "provide${clazz.innerClassNames()}" +
                        function.bindingMethodReturnType.simpleName,
                )
                .addAnnotation(Provides::class)
                .apply {
                    addParameter(
                        ParameterSpec.builder(
                            "realFactory",
                            realAssistedFactory,
                        ).build(),
                    )
                    addStatement(
                        """
                        return Default${function.assistedFactoryReturnType.simpleName}(
                            realFactory = realFactory
                        )
                        """.trimIndent(),
                    )
                }
                .returns(function.assistedFactoryReturnType)
                .build(),
        )
        .addType(
            createDefaultAssistedFactory(
                realAssistedFactory = realAssistedFactory,
                boundAssistedFactory = function.assistedFactoryReturnType,
                bindingMethodReturnType = function.bindingMethodReturnType,
                assistedFactoryFunctionName = function.assistedFactoryFunctionName,
                constructorParameters = constructorParameters,
            ),
        )
        .build()

    /**
     * Create a lambda to represent the assisted factory provided by kotlin-inject.
     *
     * When marking an injectable class with @Assisted, kotlin-inject will generate a binding in
     * lambda form to inject something that can create an instance.
     */
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

    /**
     * Create a default assisted factory concreate class that implements the assisted factory
     * interface provided in the @ContributesAssistedFactory annotation.
     */
    private fun createDefaultAssistedFactory(
        realAssistedFactory: LambdaTypeName,
        boundAssistedFactory: ClassName,
        bindingMethodReturnType: ClassName,
        assistedFactoryFunctionName: String,
        constructorParameters: List<KSValueParameter>,
    ): TypeSpec {
        return TypeSpec.classBuilder("Default${boundAssistedFactory.simpleName}")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("realFactory", realAssistedFactory).build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "realFactory",
                    realAssistedFactory,
                )
                    .initializer("realFactory")
                    .addModifiers(KModifier.PRIVATE)
                    .build(),
            )
            .addSuperinterface(boundAssistedFactory)
            .addFunction(
                FunSpec.builder(assistedFactoryFunctionName)
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameters(
                        constructorParameters
                            .filter { it.isAnnotationPresent(Assisted::class) }
                            .map { param ->
                                val paramName = param.name!!.asString()
                                val paramType = param.type.resolve().toTypeName()
                                ParameterSpec.builder(paramName, paramType).build()
                            },
                    )
                    .addStatement(
                        "return realFactory(${
                            constructorParameters.filter {
                                it.isAnnotationPresent(
                                    Assisted::class,
                                )
                            }.joinToString { it.name!!.asString() }
                        })",
                    )
                    .returns(bindingMethodReturnType)
                    .build(),

            )
            .build()
    }

    private fun checkNoDuplicateBoundTypes(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ) {
        annotations
            .mapNotNull { boundTypeFromAssistedFactory(it) }
            .map { it.declaration.requireQualifiedName() }
            .takeIf { it.isNotEmpty() }
            ?.reduce { previous, next ->
                check(previous != next, clazz) {
                    "The same type should not be contributed twice: $next."
                }

                previous
            }
    }

    private fun boundTypeFromAssistedFactory(annotation: KSAnnotation): KSType? {
        return annotation.arguments.firstOrNull { it.name?.asString() == "boundType" }
            ?.let { it.value as? KSType }
            ?.takeIf {
                it.declaration.requireQualifiedName() != Unit::class.requireQualifiedName()
            }
    }

    private fun assistedFactoryFromAnnotation(annotation: KSAnnotation): KSType {
        return annotation.arguments.firstOrNull { it.name?.asString() == "assistedFactory" }
            ?.let { it.value as? KSType }
            ?.takeIf {
                it.declaration.requireQualifiedName() != Unit::class.requireQualifiedName()
            } ?: throw IllegalArgumentException(
            "Assisted factory type must be specified in " +
                "the @ContributesAssistedFactory annotation.",
        )
    }

    private fun KSClassDeclaration.getAssistedFactoryInterfaceFunctions() = getAllFunctions()
        .filterNot {
            val simpleName = it.simpleName.asString()
            simpleName == "equals" || simpleName == "hashCode" || simpleName == "toString"
        }
        .toList()

    private fun checkIsSingleMethodInterface(type: KSType) {
        val declaration = type.declaration
        if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE) {
            val methods = declaration.getAssistedFactoryInterfaceFunctions()
            check(methods.size == 1) {
                "The assisted factory must have exactly one method."
            }
        } else {
            throw IllegalArgumentException("The assisted factory must be an interface.")
        }
    }

    private fun getSingleMethodReturnType(type: KSType): KSType? {
        val declaration = type.declaration
        if (declaration is KSClassDeclaration) {
            val methods = declaration.getAssistedFactoryInterfaceFunctions()
            if (methods.size == 1) {
                return methods[0].returnType?.resolve()
            }
        }
        return null
    }

    @Suppress("ReturnCount")
    private fun boundType(
        clazz: KSClassDeclaration,
        annotation: KSAnnotation,
    ): KSType {
        boundTypeFromAssistedFactory(annotation)?.let { return it }

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
        assistedFactory: KSType,
    ) {
        val bindingMethodReturnType: ClassName by lazy {
            boundType.toClassName()
        }
        val assistedFactoryReturnType: ClassName by lazy {
            if (assistedFactory.declaration.parentDeclaration != null) {
                val parentClassName =
                    buildClassName(assistedFactory.declaration as KSClassDeclaration)
                ClassName(parentClassName.packageName, parentClassName.simpleNames)
            } else {
                assistedFactory.toClassName()
            }
        }

        val assistedFactoryFunctionName: String by lazy {
            val classDeclaration = assistedFactory.declaration as? KSClassDeclaration
                ?: throw IllegalArgumentException(
                    "Assisted factory type must be a class.",
                )
            classDeclaration.getAllFunctions()
                .map(KSFunctionDeclaration::simpleName)
                .map { it.asString() }
                .first()
        }

        private fun buildClassName(declaration: KSClassDeclaration): ClassName {
            val parent = declaration.parentDeclaration
            return if (parent is KSClassDeclaration) {
                val parentClassName = buildClassName(parent)
                ClassName(
                    parentClassName.packageName,
                    parentClassName.simpleNames + declaration.simpleName.asString(),
                )
            } else {
                ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
            }
        }
    }
}
