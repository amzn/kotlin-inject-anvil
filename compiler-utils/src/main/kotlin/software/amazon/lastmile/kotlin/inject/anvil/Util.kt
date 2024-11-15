package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import java.util.Locale
import kotlin.reflect.KClass

/**
 * The package in which code is generated that should be picked up during the merging phase.
 */
const val LOOKUP_PACKAGE = "amazon.lastmile.inject"

/**
 * A colon-delimited string whose values are the canonical class names of custom contributing
 * annotations.
 */
const val OPTION_CONTRIBUTING_ANNOTATIONS = "kotlin-inject-anvil-contributing-annotations"

/**
 * Makes the given [String] start with a lowercase letter.
 */
fun String.decapitalize(): String = replaceFirstChar { it.lowercase(Locale.US) }

/**
 * Makes the given [String] start with an uppercase letter. Supersedes [String.capitalize],
 * which is deprecated.
 */
fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
}

/**
 * Adds an [Origin] annotation to the given [clazz].
 */
fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addOriginAnnotation(
    clazz: KSClassDeclaration,
): T = addAnnotation(
    AnnotationSpec.builder(Origin::class)
        .addMember("value = %T::class", clazz.toClassName())
        .build(),
)

/**
 * Returns the [name] argument of the receiver [KSAnnotation] as the given [T] type.
 */
inline fun <reified T> KSAnnotation.argumentOfTypeAt(
    context: ContextAware,
    name: String,
): T? {
    return argumentOfTypeWithMapperAt<T, T>(context, name) { _, value ->
        value
    }
}

@PublishedApi
internal inline fun <reified T, R> KSAnnotation.argumentOfTypeWithMapperAt(
    context: ContextAware,
    name: String,
    mapper: (arg: KSValueArgument, value: T) -> R,
): R? {
    return argumentAt(name)
        ?.let { arg ->
            val value = arg.value
            context.check(value is T, arg) {
                "Expected argument '$name' of type '${T::class.qualifiedName} but was '${arg.javaClass.name}'."
            }
            (value as T)?.let { mapper(arg, it) }
        }
}

/**
 * Returns the [name] argument of the receiver [KSAnnotation] as its [KSValueArgument], if any.
 */
fun KSAnnotation.argumentAt(name: String): KSValueArgument? {
    return arguments.find { it.name?.asString() == name }
        ?.takeUnless { it.isDefault() }
}

/**
 * Returns the qualified name of the receiver [KSDeclaration].
 */
fun KSDeclaration.requireQualifiedName(contextAware: ContextAware): String =
    contextAware.requireNotNull(qualifiedName?.asString(), this) {
        "Qualified name was null for $this"
    }

/**
 * Returns the qualified name of the receiver [KClass].
 */
fun KClass<*>.requireQualifiedName(): String = requireNotNull(qualifiedName) {
    "Qualified name was null for $this"
}
