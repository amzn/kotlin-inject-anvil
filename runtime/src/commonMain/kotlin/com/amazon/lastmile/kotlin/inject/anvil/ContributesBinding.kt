package com.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Generates a component interface for an annotated class and contributes a binding method to
 * the given [scope]. Imagine this example:
 * ```
 * interface Authenticator
 *
 * @Inject
 * @SingleInAppScope
 * class RealAuthenticator : Authenticator {
 *
 *     @ContributesTo
 *     @SingleInAppScope
 *     interface Component {
 *         @Provides fun provideAuthenticator(authenticator: RealAuthenticator): Authenticator =
 *             authenticator
 *     }
 * }
 *
 * ```
 * This is a lot of boilerplate if you always want to use `RealAuthenticator` when injecting
 * `Authenticator`. You can replace this entire component with the [ContributesBinding] annotation.
 * The equivalent would be:
 * ```
 * interface Authenticator
 *
 * @Inject
 * @SingleInAppScope
 * @ContributesBinding
 * class RealAuthenticator : Authenticator
 * ```
 * Notice that it's optional to specify [boundType], if there is only exactly one super type. If
 * there are multiple super types, then it's required to specify the parameter:
 * ```
 * @Inject
 * @SingleInAppScope
 * @ContributesBinding(
 *     boundType = Authenticator::class,
 * )
 * class RealAuthenticator : AbstractTokenProvider(), Authenticator
 * ```
 *
 * The generated component interface will automatically be associated with the scope of the
 * contributed binding. If the class is unscoped (not a singleton), then the target scope must be
 * specified in the `@ContributesTo` annotation:
 * ```
 * @Inject
 * @ContributesBinding(
 *     scope = SingleInAppScope::class,
 * )
 * class CounterPresenterImpl : CounterPresenter
 * ```
 *
 * This annotation is repeatable and a binding can be generated for multiple types:
 * ```
 * @Inject
 * @SingleInAppScope
 * @ContributesBinding(boundType = Authenticator::class)
 * @ContributesBinding(boundType = AbstractTokenProvider::class)
 * class RealAuthenticator : AbstractTokenProvider(), Authenticator
 * ```
 */
@Target(CLASS)
@Repeatable
public annotation class ContributesBinding(
    /**
     * The scope in which to include this contributed binding.
     */
    val scope: KClass<out Annotation> = Annotation::class,
    /**
     * The type that this class is bound to. When injecting [boundType] the concrete class will be
     * this annotated class.
     */
    val boundType: KClass<*> = Unit::class,
)
