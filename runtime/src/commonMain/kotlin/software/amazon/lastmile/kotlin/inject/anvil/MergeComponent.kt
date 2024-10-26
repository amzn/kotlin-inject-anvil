package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
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
 * is important, e.g. for better IDE support, then you can add the `@Component` annotation
 * directly to the class with the super type:
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
 *
 * ## Kotlin Multiplatform
 *
 * With Kotlin Multiplatform there is a high chance that the generated code cannot be referenced
 * from common Kotlin code or from common platform code like `iosMain`. This is due to how
 * [common source folders are separated from platform source folders](https://kotlinlang.org/docs/whatsnew20.html#separation-of-common-and-platform-sources-during-compilation).
 * For more details and recommendations setting up kotlin-inject in Kotlin Multiplatform projects
 * see the [official guide](https://github.com/evant/kotlin-inject/blob/main/docs/multiplatform.md).
 *
 * To address this issue, you can define an `expect fun` in the common source code next to
 * component class itself. The `actual fun` will be generated and create the component. The
 * function must be annotated with [MergeComponent.CreateComponent]. It's optional to have a
 * receiver type of `KClass` with your component type as argument. The number of parameters
 * must match the arguments of your component and the return type must be your component, e.g.
 * your component in common code could be declared as:
 * ```
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class AppComponent(
 *     @get:Provides appId: String,
 * )
 *
 * @CreateComponent
 * expect fun create(appId: String): AppComponent
 *
 * // Or with receiver type:
 * @CreateComponent
 * expect fun KClass<AppComponent>.create(appId: String): AppComponent
 * ```
 * The generated `actual fun` would look like this:
 * ```
 * actual fun create(appId: String): AppComponent {
 *     return KotlinInjectAppComponent::class.create(appId)
 * }
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
) {
    /**
     * Marks an `expect fun` in common Kotlin Multiplatform code as the builder function to
     * create the generated kotlin-inject component at runtime. This annotation is only applicable
     * for Kotlin Multiplatform, see [MergeComponent] for more details.
     */
    @Target(FUNCTION)
    public annotation class CreateComponent
}
