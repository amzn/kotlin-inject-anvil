package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.symbol.KSValueArgument

/**
 * Represents the key under which the contributed
 * element will be added to the multi-binding `Map`.
 *
 * ```
 * @MapKey
 * annotation class MyMapKey(val value: String)
 *
 * @Inject
 * @ContributesBinding(AppScope::class, multibinding = true)
 * @MyMapKey("foo")
 * class Impl : Base
 * ```
 * Where `MyMapKey` would represent the "MapKeyAnnotation".
 */
data class MapKeyAnnotation(
    val argument: KSValueArgument
)
