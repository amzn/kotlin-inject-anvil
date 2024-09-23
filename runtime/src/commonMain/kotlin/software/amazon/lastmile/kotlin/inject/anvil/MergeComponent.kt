package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Will merge all contributed component interfaces in a single interface. The generated interface
 * needs to be manually added as super type to this component, e.g.
 *
 * ```
 * @Component
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class AppComponent(
 *     ...
 * ) : AppComponentMerged
 * ```
 * The `@MergeComponent` annotation will generate the `AppComponentMerged` interface in the
 * same package as `AppComponent`.
 *
 * It's possible to exclude any automatically added component interfaces with the [exclude]
 * parameter if needed.
 *
 * ```
 * @MergeComponent(
 *     scope = AppScope::class,
 *     exclude = [
 *       ComponentInterface::class,
 *       Other.Component::class,
 *     ]
 * )
 * interface AppComponent
 * ```
 */
@Target(CLASS)
public annotation class MergeComponent(
    /**
     * The scope in which to include this contributed component interface.
     */
    val scope: KClass<*>,

    /**
     * List of component interfaces that are contributed to the same scope, but should be
     * excluded from the component.
     */
    val exclude: Array<KClass<*>> = [],
)
