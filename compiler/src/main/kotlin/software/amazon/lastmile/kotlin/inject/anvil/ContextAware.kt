package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
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
import com.google.devtools.ksp.symbol.Visibility
import me.tatarka.inject.annotations.Scope
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import kotlin.reflect.KClass

@Suppress("TooManyFunctions")
internal interface ContextAware {
    val logger: KSPLogger

    private val scopeFqName get() = Scope::class.requireQualifiedName()

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
        clazz: KSClassDeclaration,
        lazyMessage: () -> String = { "Contributed component interfaces must be public." },
    ) {
        check(clazz.getVisibility() == Visibility.PUBLIC, clazz, lazyMessage)
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
            "Couldn't find scope annotation for $this."
        }
    }

    private fun KSClassDeclaration.scopeOrNull(): MergeScope? {
        val annotationsWithScopeParameter = annotations.filter {
            // Avoid scope annotations themselves, e.g. that skips `@SingleIn` and include
            // annotations with a "scope" parameter, e.g. `@ContributesTo`.
            !isScopeAnnotation(it) && it.hasScopeParameter()
        }.toList()

        return if (annotationsWithScopeParameter.isEmpty()) {
            annotations.firstOrNull { isScopeAnnotation(it) }
                ?.let { MergeScope(this@ContextAware, it) }
        } else {
            scopeForAnnotationsWithScopeParameters(this, annotationsWithScopeParameter)
        }
    }

    fun isScopeAnnotation(annotation: KSAnnotation): Boolean {
        return isScopeAnnotation(annotation.annotationType.resolve())
    }

    private fun isScopeAnnotation(type: KSType): Boolean {
        return type.declaration.annotations.any {
            it.annotationType.resolve().declaration.requireQualifiedName() == scopeFqName
        }
    }

    private fun KSAnnotation.hasScopeParameter(): Boolean {
        return (annotationType.resolve().declaration as? KSClassDeclaration)
            ?.primaryConstructor?.parameters?.firstOrNull()?.name?.asString() == "scope"
    }

    private fun scopeForAnnotationsWithScopeParameters(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ): MergeScope {
        val explicitScopes = annotations.mapNotNull { annotation ->
            annotation.scopeParameter(this)
        }

        val classScope = clazz.annotations.firstOrNull { isScopeAnnotation(it) }
            ?.let { MergeScope(this, it) }

        if (explicitScopes.isNotEmpty()) {
            check(explicitScopes.size == annotations.size, clazz) {
                "If one annotation has an explicit scope, then all " +
                    "annotations must specify an explicit scope."
            }

            explicitScopes.scan(
                explicitScopes.first().declaration.requireQualifiedName(),
            ) { previous, next ->
                check(previous == next.declaration.requireQualifiedName(), clazz) {
                    "All explicit scopes on annotations must be the same."
                }
                previous
            }

            val explicitScope = explicitScopes.first()
            val explicitScopeIsScope = isScopeAnnotation(explicitScope)

            return if (explicitScopeIsScope) {
                MergeScope(
                    contextAware = this,
                    annotationType = explicitScope,
                    markerType = null,
                )
            } else {
                MergeScope(
                    contextAware = this,
                    annotationType = null,
                    markerType = explicitScope,
                )
            }
        }

        return requireNotNull(classScope, clazz) {
            "Couldn't find scope for ${clazz.simpleName.asString()}. For unscoped " +
                "objects it is required to specify the target scope on the annotation."
        }
    }

    fun KSClassDeclaration.origin(): KSClassDeclaration {
        val annotation = findAnnotation(Origin::class)

        val argument = annotation.arguments.firstOrNull { it.name?.asString() == "value" }
            ?: annotation.arguments.first()

        return (argument.value as KSType).declaration as KSClassDeclaration
    }

    fun KSClassDeclaration.contributedSubcomponent(): KSClassDeclaration {
        return origin().parentDeclaration as KSClassDeclaration
    }

    fun KSClassDeclaration.findAnnotation(annotation: KClass<out Annotation>): KSAnnotation =
        findAnnotations(annotation).single()

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

    fun KSDeclaration.requireContainingFile(): KSFile = requireNotNull(containingFile, this) {
        "Containing file was null for $this"
    }

    fun KSDeclaration.requireQualifiedName(): String = requireQualifiedName(this@ContextAware)

    fun Resolver.getSymbolsWithAnnotation(annotation: KClass<*>): Sequence<KSAnnotated> =
        getSymbolsWithAnnotation(annotation.requireQualifiedName())

    fun KSDeclaration.innerClassNames(separator: String = ""): String {
        val classNames = requireQualifiedName().substring(packageName.asString().length + 1)
        return classNames.replace(".", separator)
    }

    /**
     * Return `software.amazon.Test` into `SoftwareAmazonTest`.
     */
    val KSClassDeclaration.safeClassName: String
        get() = requireQualifiedName()
            .split(".")
            .joinToString(separator = "") { it.capitalize() }
}
