package com.amazon.lastmile.kotlin.inject.anvil.sample

import me.tatarka.inject.annotations.Scope

/**
 * A scope annotation for DI to make classes singletons in the app scope:
 * ```
 * @Inject
 * @SingleInAppScope
 * class MyClass(..) : SuperType {
 *     ...
 * }
 * ```
 */
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class SingleInAppScope
