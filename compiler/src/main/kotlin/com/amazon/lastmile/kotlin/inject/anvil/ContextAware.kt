package com.amazon.lastmile.kotlin.inject.anvil

import com.amazon.lastmile.kotlin.inject.anvil.internal.Origin
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

    fun checkIsPublic(clazz: KSClassDeclaration) {
        check(clazz.getVisibility() == Visibility.PUBLIC, clazz) {
            "Contributed component interfaces must be public."
        }
    }

    fun checkIsInterface(clazz: KSClassDeclaration) {
        check(clazz.classKind == ClassKind.INTERFACE, clazz) {
            "Only interfaces can be contributed."
        }
    }

    fun KSClassDeclaration.scope(): KSAnnotation {
        return requireNotNull(scopeOrNull(), this) {
            "Couldn't find scope annotation for $this."
        }
    }

    fun KSClassDeclaration.scopeOrNull(): KSAnnotation? =
        annotations.firstOrNull { isScopeAnnotation(it) }

    private fun isScopeAnnotation(annotation: KSAnnotation): Boolean {
        return annotation.annotationType.resolve().declaration.annotations.any {
            it.annotationType.resolve().declaration.requireQualifiedName() == scopeFqName
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
            .also {
                check(it.isNotEmpty(), this) {
                    "Couldn't find the @${annotation.simpleName} annotation for $this."
                }
            }
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

    fun KSDeclaration.requireQualifiedName(): String =
        requireNotNull(qualifiedName?.asString(), this) {
            "Qualified name was null for $this"
        }

    fun KClass<*>.requireQualifiedName(): String = requireNotNull(qualifiedName) {
        "Qualified name was null for $this"
    }

    fun Resolver.getSymbolsWithAnnotation(annotation: KClass<*>): Sequence<KSAnnotated> =
        getSymbolsWithAnnotation(annotation.requireQualifiedName())

    fun KSAnnotation.isSameAs(other: KSAnnotation): Boolean {
        return annotationType.resolve().declaration.requireQualifiedName() ==
            other.annotationType.resolve().declaration.requireQualifiedName()
    }

    fun KSDeclaration.innerClassNames(separator: String = ""): String {
        val classNames = requireQualifiedName().substring(packageName.asString().length + 1)
        return classNames.replace(".", separator)
    }

    /**
     * Return `com.amazon.Test` into `ComAmazonTest`.
     */
    val KSClassDeclaration.safeClassName: String
        get() = requireQualifiedName()
            .split(".")
            .joinToString(separator = "") { it.capitalize() }
}
