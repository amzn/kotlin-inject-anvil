package software.amazon.lastmile.kotlin.inject.anvil

import kotlin.reflect.KClass

/**
 * Marks an annotation class as a key for a multi-binding `Map`.
 *
 * When used with a [ContributesBinding] annotation, this annotation specifies the key under
 * which the contributed element will be added to the map.
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
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class MapKey

@MapKey
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class StringKey(
    val value: String,
)

@MapKey
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class IntKey(
    val value: Int,
)

@MapKey
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class LongKey(
    val value: Long,
)

@MapKey
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class ClassKey(
    val value: KClass<*>,
)
