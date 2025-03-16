package software.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * API to get the application ID. This is used in assisted injection scenarios to provide the
 * application ID to the object graph with an extra parameter provided at runtime.
 */
interface AssistedApplicationIdProvider {
    val applicationId: String
}

@Inject
@ContributesBinding(AppScope::class, boundType = AssistedApplicationIdProvider::class)
@SingleIn(AppScope::class)
class RealAssistedIdProvider(
    @Assisted private val prependedName: String,
    private val applicationIdProvider: ApplicationIdProvider,
) : AssistedApplicationIdProvider {
    override val applicationId: String
        get() = "$prependedName-${applicationIdProvider.appId}"
}
