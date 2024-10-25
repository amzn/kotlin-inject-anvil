package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Will merge all contributed component interfaces in a single interface. It's not required
 * to add the original `@Component` annotation from kotlin-inject to your component. This
 * annotation will generate the final kotlin-inject component under the hood:
 * ```
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class AppComponent(
 *     ...
 * )
 * ```
 * Through an extension function on the class object the component can be instantiated:
 * ```
 * val component = AppComponent::class.create(...)
 * ```
 *
 * Note that in this example `AppComponent` will not implement all contributed interfaces directly.
 * Instead, the final generated kotlin-inject component will contain all contributions. If this
 * is important, e.g. for better IDE support, then you can the `@Component` annotation directly
 * to the class with the super type:
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
 * ## Exclusions
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
