package software.amazon.lastmile.kotlin.inject.anvil.sample

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class AndroidAppComponentTest {

    @Test
    fun `the Greeter is provided by the app component`() {
        val component = component<GreeterComponent>()
        assertThat(component.greeter.greet()).isEqualTo("Hello from Android")
    }

    private fun <T> component(): T {
        @Suppress("UNCHECKED_CAST")
        return AndroidAppComponent::class.create(application()) as T
    }

    private fun application(): Application = object : Application() {
        override fun getPackageName(): String = "software.amazon.test"
    }
}
