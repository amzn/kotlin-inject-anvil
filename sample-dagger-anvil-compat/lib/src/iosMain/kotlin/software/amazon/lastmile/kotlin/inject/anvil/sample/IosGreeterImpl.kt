package software.amazon.lastmile.kotlin.inject.anvil.sample

import com.squareup.anvil.annotations.ContributesBinding
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * iOS implementation for the [Greeter] interface. It uses the [ContributesBinding] annotation
 * from Dagger Anvil to contribute a binding for the [Greeter] interface to the [AppScope].
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosGreeterImpl : Greeter {
    override fun greet(): String = "Hello from iOS"
}
