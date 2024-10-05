package software.amazon.lastmile.kotlin.inject.anvil

import me.tatarka.inject.annotations.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
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
 */
@Qualifier
@Retention(RUNTIME)
@Target(CLASS, FUNCTION, PROPERTY_GETTER, VALUE_PARAMETER, TYPE, PROPERTY)
public actual annotation class ForScope(
    /**
     * The marker that identifies this scope.
     */
    actual val scope: KClass<*>,
)
