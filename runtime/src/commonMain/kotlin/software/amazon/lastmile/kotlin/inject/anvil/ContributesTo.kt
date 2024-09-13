package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Marks a component interface to be included in the dependency graph in the given [scope].
 * The processor will automatically add the interface as super type to the final component
 * marked with [MergeComponent].
 * ```
 * @ContributesTo(AppScope::class)
 * interface ComponentInterface { .. }
 * ```
 *
 *
 * ## Custom scopes
 *
 * If you use your own scope annotations without the references such as `AppScope::class`, then
 * you can use your scope directly on the class:
 * ```
 * @ContributesTo
 * @Singleton
 * interface ComponentInterface { .. }
 * ```
 */
@Target(CLASS)
public annotation class ContributesTo(
    /**
     * The scope in which to include this contributed component interface.
     */
    val scope: KClass<*> = Unit::class,
)
