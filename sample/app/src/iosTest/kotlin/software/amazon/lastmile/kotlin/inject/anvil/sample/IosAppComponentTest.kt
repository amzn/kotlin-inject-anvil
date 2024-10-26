package software.amazon.lastmile.kotlin.inject.anvil.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import platform.UIKit.UIApplication
import kotlin.test.Test

class IosAppComponentTest {

    @Test
    @Suppress("FunctionNaming")
    fun `the ApplicationIdProvider is provided by the app component`() {
        val component = component<ApplicationIdProviderComponent>()
        assertThat(component.applicationIdProvider.appId).isEqualTo("UIApplicationStateActive")
    }

    private fun <T> component(): T {
        @Suppress("UNCHECKED_CAST")
        return IosAppComponent::class.createComponent(UIApplication.sharedApplication) as T
    }
}
