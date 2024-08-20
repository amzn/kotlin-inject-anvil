package com.amazon.lastmile.kotlin.inject.anvil.sample

import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import me.tatarka.inject.annotations.Inject
import platform.UIKit.UIApplication

/**
 * iOS implementation for [ApplicationIdProvider] and provides a fake string as application
 * ID.
 *
 * This class is a singleton and automatically provided in the dependency graph whenever you
 * inject [ApplicationIdProvider] through the [ContributesBinding] annotation.
 */
@Inject
@SingleInAppScope
@ContributesBinding
class IosApplicationIdProvider(
    application: UIApplication,
) : ApplicationIdProvider {
    // This is not the actual appId, but demonstrates that iOS specific classes
    // can be injected.
    override val appId: String = application.applicationState.name
}
