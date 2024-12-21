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
 * ## Replacement
 *
 * Component interfaces can replace other component interfaces with the [replaces] parameter.
 * This is especially helpful for components interfaces providing different bindings in tests.
 * ```
 * @ContributesTo(
 *     scope = AppScope::class,
 *     replaces = [ComponentInterface::class],
 * )
 * interface TestComponentInterface { .. }
 * ```
 */
@Target(CLASS)
public annotation class ContributesTo(
    /**
     * The scope in which to include this contributed component interface.
     */
    val scope: KClass<*>,
    /**
     * This contributed component will replace these contributed classes. The array is allowed to
     * include other contributed bindings and component interfaces. All replaced classes must
     * use the same scope.
     */
    val replaces: Array<KClass<*>> = [],
)
