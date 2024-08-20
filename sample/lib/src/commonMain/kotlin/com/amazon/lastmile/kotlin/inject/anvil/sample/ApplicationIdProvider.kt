package com.amazon.lastmile.kotlin.inject.anvil.sample

/**
 * API to get the application ID. There are different implementations for Android and iOS and
 * their implementations are bound to this interface through code-gen.
 */
interface ApplicationIdProvider {
    /**
     * The application ID. This string is provided by the host platform.
     */
    val appId: String
}
