package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.reflect.KClass

/**
 * A scope annotation for kotlin-inject to make classes singletons in the specified [scope],
 * e.g. to make a class a singleton in the application scope you'd use:
 * ```
 * @Inject
 * @SingleIn(AppScope::class)
 * class MyClass(..) : SuperType {
 *     ...
 * }
 * ```
 *
 * This annotation is also marked with JSR-330 annotations and therefore the same annotation
 * can be used for Dagger 2 and Anvil.
 */
@me.tatarka.inject.annotations.Scope
@javax.inject.Scope
@Retention(RUNTIME)
@Target(CLASS, FUNCTION, PROPERTY_GETTER, VALUE_PARAMETER)
public actual annotation class SingleIn(
    /**
     * The marker that identifies this scope.
     */
    actual val scope: KClass<*>,
)
