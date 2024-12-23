package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Generates a component interface for an annotated class and contributes a binding method to
 * the given [scope]. Imagine this example:
 * ```
 * interface Authenticator
 *
 * @Inject
 * @SingleIn(AppScope::class)
 * class RealAuthenticator : Authenticator {
 *
 *     @ContributesTo(AppScope::class)
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
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class)
 * class RealAuthenticator : Authenticator
 * ```
 * Notice that it's optional to specify [boundType], if there is only exactly one super type. If
 * there are multiple super types, then it's required to specify the parameter:
 * ```
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(
 *     scope = AppScope::class,
 *     boundType = Authenticator::class,
 * )
 * class RealAuthenticator : AbstractTokenProvider(), Authenticator
 * ```
 *
 * This annotation is repeatable and a binding can be generated for multiple types:
 * ```
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, boundType = Authenticator::class)
 * @ContributesBinding(AppScope::class, boundType = AbstractTokenProvider::class)
 * class RealAuthenticator : AbstractTokenProvider(), Authenticator
 * ```
 *
 * ## Multi-bindings
 *
 * Bindings can be optionally contributed to a multi-binding `Set` by setting the [multibinding]
 * property to `true`.
 *
 * ```
 * @Inject
 * @ContributesBinding(AppScope::class, boundType = Base::class)
 * @ContributesBinding(AppScope::class, boundType = Base2::class, multibinding = true)
 * class Impl : Base, Base2
 * ```
 *
 * ## Replacement
 *
 * Contributed bindings can replace other contributed bindings and component interfaces with
 * the [replaces] parameter. This is especially helpful for different bindings in tests.
 * ```
 * @Inject
 * @ContributesBinding(
 *     scope = AppScope::class,
 *     replaces = [RealAuthenticator::class]
 * )
 * class FakeAuthenticator : Authenticator
 * ```
 */
@Target(CLASS)
@Repeatable
public annotation class ContributesBinding(
    /**
     * The scope in which to include this contributed binding.
     */
    val scope: KClass<*>,
    /**
     * The type that this class is bound to. When injecting [boundType] the concrete class will be
     * this annotated class.
     */
    val boundType: KClass<*> = Unit::class,
    /**
     * Indicates that this binding is a multibinding. If true, the generated provider will be
     * annotated with `@IntoSet`.
     */
    val multibinding: Boolean = false,
    /**
     * This contributed binding will replace these contributed classes. The array is allowed to
     * include other contributed bindings and contributed component interfaces. All replaced
     * classes must use the same scope.
     */
    val replaces: Array<KClass<*>> = [],
)
