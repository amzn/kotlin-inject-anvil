package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.symbol.KSType

/**
 * Represents the destination of contributed types and which types should be merged during
 * the merge phase, e.g.
 * ```
 * @ContributesTo(AppScope::class)
 * interface ContributedComponentInterface
 *
 * @Component
 * @MergeComponent(AppScope::class)
 * interface MergedComponent
 * ```
 * Where `AppScope` would represent the "MergeScope".
 */
internal data class MergeScope(
    val type: KSType,
)
