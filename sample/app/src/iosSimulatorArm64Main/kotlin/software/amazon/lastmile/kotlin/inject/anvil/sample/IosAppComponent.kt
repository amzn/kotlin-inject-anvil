package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import platform.UIKit.UIApplication
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

/**
 * Concrete application component for iOS using the scope [SingleInAppScope].
 * [IosAppComponentMerged] is a generated interface. Through this merged interface
 * [ApplicationIdProviderComponent], other contributed component interfaces and contributed
 * bindings such as from [IosApplicationIdProvider] are implemented.
 *
 * Note that this component lives in an iOS source folder and therefore types such as
 * [UIApplication] can be provided in the object graph.
 */
@Component
@MergeComponent
@SingleInAppScope
abstract class IosAppComponent(
    /**
     * The iOS application that is provided to this object graph.
     */
    @get:Provides val application: UIApplication,
) : IosAppComponentMerged
