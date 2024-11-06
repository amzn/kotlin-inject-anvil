package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@com.squareup.anvil.annotations.ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidGreeterImpl : Greeter {
    override fun greet(): String = "Hello from Android"
}
