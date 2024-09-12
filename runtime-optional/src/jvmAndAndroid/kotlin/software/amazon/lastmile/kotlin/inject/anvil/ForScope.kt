package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.reflect.KClass

/**
 * Commonly shared qualifier to tie types provided in multiple scopes to a particular [scope], e.g.
 * ```
 * @Inject
 * @ContributesBinding(AppScope::class)
 * @ForScope(AppScope::class)
 * class UnauthenticatedHttpClient : HttpClient
 *
 * @Inject
 * @ContributesBinding(LoggedInScope::class)
 * @ForScope(LoggedInScope::class)
 * class AuthenticatedHttpClient : HttpClient
 * ```
 *
 * This annotation is also marked with JSR-330 annotations and therefore the same annotation
 * can be used for Dagger 2 and Anvil.
 */
@me.tatarka.inject.annotations.Qualifier
@javax.inject.Qualifier
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
public actual annotation class ForScope(
    /**
     * The marker that identifies this scope.
     */
    actual val scope: KClass<*>,
)
