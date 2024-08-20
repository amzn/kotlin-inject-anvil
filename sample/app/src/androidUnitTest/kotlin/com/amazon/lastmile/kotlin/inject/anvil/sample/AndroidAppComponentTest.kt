package com.amazon.lastmile.kotlin.inject.anvil.sample

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class AndroidAppComponentTest {

    @Test
    fun `the ApplicationIdProvider is provided by the app component`() {
        val component = component<ApplicationIdProviderComponent>()
        assertThat(component.applicationIdProvider.appId).isEqualTo("com.amazon.test")
    }

    private fun <T> component(): T {
        @Suppress("UNCHECKED_CAST")
        return AndroidAppComponent::class.create(application()) as T
    }

    private fun application(): Application = object : Application() {
        override fun getPackageName(): String = "com.amazon.test"
    }
}
