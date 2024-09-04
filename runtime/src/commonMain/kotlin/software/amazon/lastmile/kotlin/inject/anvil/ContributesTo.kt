package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Marks a component interface to be included in the dependency graph in the given `scope`.
 * The processor will automatically add the interface as super type to the final component
 * marked with [MergeComponent].
 *
 * ```
 * @ContributesTo
 * @SingleInAppScope
 * interface ComponentInterface { .. }
 * ```
 */
@Target(CLASS)
public annotation class ContributesTo
