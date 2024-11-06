package software.amazon.lastmile.kotlin.inject.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

/**
 * This component interface is added to the kotlin-inject component using the scope
 * [AppScope] automatically, by using the [ContributesTo] annotation from Dagger Anvil.
 */
@ContributesTo(AppScope::class)
interface GreeterComponent {
    /**
     * Provides the [Greeter] from the kotlin-inject object graph.
     */
    val greeter: Greeter
}
