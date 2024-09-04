package software.amazon.lastmile.kotlin.inject.anvil.internal

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass

/**
 * DO NOT USE DIRECTLY.
 *
 * Marker for generated component interface to link their origin.
 */
@Target(CLASS, PROPERTY)
public annotation class Origin(
    /**
     * Reference to the class that triggered generating this component interface.
     */
    val value: KClass<*>,
)
