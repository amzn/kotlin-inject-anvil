package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Qualifier
import me.tatarka.inject.annotations.Scope
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import kotlin.reflect.KClass

@Suppress("TooManyFunctions", "UndocumentedPublicClass", "UndocumentedPublicFunction")
interface ContextAware {
    /**
     * The KSP logger to use by the processor.
     */
    val logger: KSPLogger

    private val scopeFqName get() = Scope::class.requireQualifiedName()
    private val componentFqName get() = Component::class.requireQualifiedName()

    fun <T : Any> requireNotNull(
        value: T?,
        symbol: KSNode?,
        lazyMessage: () -> String,
    ): T {
        if (value == null) {
            val message = lazyMessage()
            logger.error(message, symbol)
            throw IllegalArgumentException(message)
        }

        return value
    }

    fun check(
        condition: Boolean,
        symbol: KSNode?,
        lazyMessage: () -> String,
    ) {
        if (!condition) {
            val message = lazyMessage()
            logger.error(message, symbol)
            throw IllegalStateException(message)
        }
    }

    fun checkIsPublic(
        declaration: KSDeclaration,
        lazyMessage: () -> String = { "Contributed component interfaces must be public." },
    ) {
        check(declaration.getVisibility() == Visibility.PUBLIC, declaration, lazyMessage)
    }

    fun checkNotPrivate(
        declaration: KSDeclaration,
        lazyMessage: () -> String = { "Contribute component interfaces must not be private." },
    ) {
        check(declaration.getVisibility() != Visibility.PRIVATE, declaration, lazyMessage)
    }

    fun checkIsInterface(
        clazz: KSClassDeclaration,
        lazyMessage: () -> String = { "Only interfaces can be contributed." },
    ) {
        check(clazz.classKind == ClassKind.INTERFACE, clazz, lazyMessage)
    }

    fun checkHasScope(clazz: KSClassDeclaration) {
        // Ensures that the value is non-null.
        clazz.scope()
    }

    fun KSClassDeclaration.scope(): MergeScope {
        return requireNotNull(scopeOrNull(), this) {
            "Couldn't find scope for $this."
        }
    }

    fun KSClassDeclaration.scopeOrNull(): MergeScope? {
        val annotationsWithScopeParameter = annotations.filter { it.hasScopeParameter() }
            .toList()
            .ifEmpty { return null }

        return scopeForAnnotationsWithScopeParameters(this, annotationsWithScopeParameter)
    }

    fun KSAnnotation.isKotlinInjectScopeAnnotation(): Boolean {
        return annotationType.resolve().isKotlinInjectScopeAnnotation()
    }

    private fun KSType.isKotlinInjectScopeAnnotation(): Boolean {
        return declaration.annotations.any {
            it.annotationType.resolve().declaration.requireQualifiedName() == scopeFqName
        }
    }

    fun KSAnnotation.isKotlinInjectQualifierAnnotation(): Boolean {
        return annotationType.resolve().isKotlinInjectQualifierAnnotation()
    }

    @OptIn(KspExperimental::class)
    private fun KSType.isKotlinInjectQualifierAnnotation(): Boolean {
        return declaration.isAnnotationPresent(Qualifier::class)
    }

    fun KSAnnotation.isKotlinInjectComponentAnnotation(): Boolean {
        return annotationType.resolve().declaration.requireQualifiedName() == componentFqName
    }

    private fun KSAnnotation.hasScopeParameter(): Boolean {
        return (annotationType.resolve().declaration as? KSClassDeclaration)
            ?.primaryConstructor?.parameters?.firstOrNull()?.name?.asString() == "scope"
    }

    private fun scopeForAnnotationsWithScopeParameters(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ): MergeScope {
        val explicitScopes = annotations.map { annotation ->
            annotation.scopeParameter()
        }

        explicitScopes.scan(
            explicitScopes.first().declaration.requireQualifiedName(),
        ) { previous, next ->
            check(previous == next.declaration.requireQualifiedName(), clazz) {
                "All scopes on annotations must be the same."
            }
            previous
        }

        return MergeScope(explicitScopes.first())
    }

    private fun KSAnnotation.scopeParameter(): KSType {
        return requireNotNull(scopeParameterOrNull(), this) {
            "Couldn't find a scope parameter."
        }
    }

    private fun KSAnnotation.scopeParameterOrNull(): KSType? {
        return arguments.firstOrNull { it.name?.asString() == "scope" }
            ?.let { it.value as? KSType }
    }

    fun KSClassDeclaration.originOrNull(): KSClassDeclaration? {
        val annotation = findAnnotations(Origin::class).singleOrNull() ?: return null

        val argument = annotation.arguments.firstOrNull { it.name?.asString() == "value" }
            ?: annotation.arguments.first()

        return (argument.value as KSType).declaration as KSClassDeclaration
    }

    fun KSClassDeclaration.origin(): KSClassDeclaration {
        return requireNotNull(originOrNull(), this) {
            "Origin annotation not found."
        }
    }

    fun KSClassDeclaration.contributedSubcomponent(): KSClassDeclaration {
        return origin().parentDeclaration as KSClassDeclaration
    }

    fun KSClassDeclaration.findAnnotation(annotation: KClass<out Annotation>): KSAnnotation =
        findAnnotations(annotation).single()

    fun KSClassDeclaration.findAnnotationOrNull(annotation: KClass<out Annotation>): KSAnnotation? =
        findAnnotations(annotation).singleOrNull()

    fun KSClassDeclaration.findAnnotations(annotation: KClass<out Annotation>): List<KSAnnotation> {
        val fqName = annotation.requireQualifiedName()
        return annotations.filter { it.isAnnotation(fqName) }.toList()
    }

    fun KSAnnotation.isAnnotation(fqName: String): Boolean {
        return annotationType.resolve().declaration.requireQualifiedName() == fqName
    }

    fun KSClassDeclaration.factoryFunctions(): Sequence<KSFunctionDeclaration> {
        return getDeclaredFunctions().filter { it.isAbstract }
    }

    fun requireKotlinInjectScope(clazz: KSClassDeclaration): KSAnnotation {
        return requireNotNull(
            clazz.annotations.firstOrNull { it.isKotlinInjectScopeAnnotation() },
            clazz,
        ) {
            "A kotlin-inject scope like @SingleIn(Abc::class) is missing."
        }
    }

    fun KSDeclaration.requireContainingFile(): KSFile = requireNotNull(containingFile, this) {
        "Containing file was null for $this"
    }

    fun KSDeclaration.requireQualifiedName(): String = requireQualifiedName(this@ContextAware)

    fun KSValueParameter.requireName(): String = requireNotNull(name, this) {
        "The name of the parameter $this was null."
    }.asString()

    fun Resolver.getSymbolsWithAnnotation(annotation: KClass<*>): Sequence<KSAnnotated> =
        getSymbolsWithAnnotation(annotation.requireQualifiedName())

    fun Resolver.getSymbolsWithAnnotations(vararg annotations: KClass<*>): Sequence<KSAnnotated> =
        sequenceOf(*annotations).flatMap { getSymbolsWithAnnotation(it) }.distinct()

    fun KSDeclaration.innerClassNames(separator: String = ""): String {
        val classNames = requireQualifiedName().substring(packageName.asString().length + 1)
        return classNames.replace(".", separator)
    }

    fun KSAnnotation.getReplaces(): List<KSClassDeclaration> {
        val argument = arguments.firstOrNull { it.name?.asString() == "replaces" }
            ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        return (argument.value as? List<KSType>)
            ?.map { it.declaration as KSClassDeclaration }
            ?: emptyList()
    }

    fun checkReplacesHasSameScope(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ) {
        val scope = clazz.scope()

        annotations
            .flatMap { it.getReplaces() }
            .map { replacedClass ->
                checkHasScope(replacedClass)

                check(scope == replacedClass.scope(), clazz) {
                    "Replaced types must use the same scope. ${clazz.requireQualifiedName()} " +
                        "uses scope ${scope.type}, but tries to replace " +
                        "${replacedClass.requireQualifiedName()} using scope " +
                        "${replacedClass.scope().type}."
                }
            }
    }

    /**
     * Return `software.amazon.Test` into `SoftwareAmazonTest`.
     */
    val KSClassDeclaration.safeClassName: String
        get() = requireQualifiedName()
            .split(".")
            .joinToString(separator = "") { it.capitalize() }

    /**
     * Returns the merged class name for the receiver [KSClassDeclaration].
     */
    val KSClassDeclaration.mergedClassName get() = "${innerClassNames()}Merged"
}
