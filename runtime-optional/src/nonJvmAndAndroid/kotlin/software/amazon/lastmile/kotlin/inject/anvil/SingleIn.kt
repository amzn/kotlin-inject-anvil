package software.amazon.lastmile.kotlin.inject.anvil

import me.tatarka.inject.annotations.Scope
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
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
 */
@Scope
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
public actual annotation class SingleIn(
    /**
     * The marker that identifies this scope.
     */
    actual val scope: KClass<*>,
)
