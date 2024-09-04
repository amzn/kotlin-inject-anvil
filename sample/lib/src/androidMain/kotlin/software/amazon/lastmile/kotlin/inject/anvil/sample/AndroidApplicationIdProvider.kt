package software.amazon.lastmile.kotlin.inject.anvil.sample

import android.app.Application
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * Android implementation for [ApplicationIdProvider] and provides the package name as application
 * ID.
 *
 * This class is a singleton and automatically provided in the dependency graph whenever you
 * inject [ApplicationIdProvider] through the [ContributesBinding] annotation.
 */
@Inject
@SingleInAppScope
@ContributesBinding
class AndroidApplicationIdProvider(
    application: Application,
) : ApplicationIdProvider {
    override val appId: String = application.packageName
}
