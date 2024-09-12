package software.amazon.lastmile.kotlin.inject.anvil.sample

import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

/**
 * This component interface is added to the kotlin-inject component using the scope
 * [AppScope] automatically.
 */
@ContributesTo(AppScope::class)
interface ApplicationIdProviderComponent {
    /**
     * Provides the [ApplicationIdProvider] from the kotlin-inject object graph.
     */
    val applicationIdProvider: ApplicationIdProvider
}
