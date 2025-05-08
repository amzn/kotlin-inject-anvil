package software.amazon.lastmile.kotlin.inject.anvil

import me.tatarka.inject.annotations.Scope
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
 * For Android and JVM targets this annotation is also marked with JSR-330 annotations and
 * therefore the same annotation can be used for Dagger 2 and Anvil.
 */
@Scope
@Target(CLASS, FUNCTION, PROPERTY_GETTER, VALUE_PARAMETER)
public expect annotation class SingleIn(
    /**
     * The marker that identifies this scope.
     */
    val scope: KClass<*>,
)
