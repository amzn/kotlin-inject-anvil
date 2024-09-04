package software.amazon.lastmile.kotlin.inject.anvil.internal

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * DO NOT USE DIRECTLY.
 *
 * Marker for generated component interface when it's using `@ContributesSubcomponent`.
 */
@Target(CLASS)
public annotation class Subcomponent
