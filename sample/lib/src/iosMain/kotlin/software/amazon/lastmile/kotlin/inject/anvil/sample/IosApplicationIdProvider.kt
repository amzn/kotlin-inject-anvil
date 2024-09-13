package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Inject
import platform.UIKit.UIApplication
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * iOS implementation for [ApplicationIdProvider] and provides a fake string as application
 * ID.
 *
 * This class is a singleton and automatically provided in the dependency graph whenever you
 * inject [ApplicationIdProvider] through the [ContributesBinding] annotation.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosApplicationIdProvider(
    application: UIApplication,
) : ApplicationIdProvider {
    // This is not the actual appId, but demonstrates that iOS specific classes
    // can be injected.
    override val appId: String = application.applicationState.name
}
