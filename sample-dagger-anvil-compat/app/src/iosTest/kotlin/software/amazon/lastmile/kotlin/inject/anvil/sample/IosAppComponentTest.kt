package software.amazon.lastmile.kotlin.inject.anvil.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import platform.UIKit.UIApplication
import kotlin.test.Test

class IosAppComponentTest {

    @Test
    fun `the Greeter is provided by the app component`() {
        val component = component<GreeterComponent>()
        assertThat(component.greeter.greet()).isEqualTo("Hello from iOS")
    }

    private fun <T> component(): T {
        @Suppress("UNCHECKED_CAST")
        return IosAppComponent::class.createComponent(UIApplication.sharedApplication) as T
    }
}
