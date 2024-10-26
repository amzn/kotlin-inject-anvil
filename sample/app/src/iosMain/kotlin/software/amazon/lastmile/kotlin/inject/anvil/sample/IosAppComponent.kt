package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Provides
import platform.UIKit.UIApplication
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.KClass

/**
 * Concrete application component for iOS using the scope [SingleIn] [AppScope].
 * The final kotlin-inject component is generated and will extend
 * [ApplicationIdProviderComponent], other contributed component interfaces and contributed
 * bindings such as from [IosApplicationIdProvider].
 *
 * Note that this component lives in an iOS source folder and therefore types such as
 * [UIApplication] can be provided in the object graph.
 */
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent(
    /**
     * The iOS application that is provided to this object graph.
     */
    @get:Provides val application: UIApplication,
)

/**
 * The `actual fun` will be generated for each iOS specific target. See [MergeComponent] for
 * more details.
 */
@MergeComponent.CreateComponent
expect fun KClass<IosAppComponent>.createComponent(application: UIApplication): IosAppComponent
