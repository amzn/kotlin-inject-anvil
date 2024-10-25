package software.amazon.lastmile.kotlin.inject.anvil.sample

import android.app.Application
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Concrete application component for Android using the scope [SingleIn] [AppScope].
 * The final kotlin-inject component is generated and will extend
 * [ApplicationIdProviderComponent], other contributed component interfaces and contributed
 * bindings such as from [AndroidApplicationIdProvider].
 *
 * Note that this component lives in an Android source folder and therefore types such as
 * [Application] can be provided in the object graph.
 */
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AndroidAppComponent(
    /**
     * The Android application that is provided to this object graph.
     */
    @get:Provides val application: Application,
)
