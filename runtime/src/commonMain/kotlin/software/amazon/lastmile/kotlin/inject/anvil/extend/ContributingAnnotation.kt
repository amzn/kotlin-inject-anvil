package software.amazon.lastmile.kotlin.inject.anvil.extend

import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS

/**
 * kotlin-inject-anvil is extensible. You can you create your own annotations and write
 * your own KSP processors to generate code. kotlin-inject-anvil supports multiple rounds and
 * will pick up the generated code.
 *
 * When defining an annotation that will generate new code, make sure to annotate this
 * annotation class with [ContributingAnnotation]. The annotation will generate a marker for
 * kotlin-inject-anvil's merge phase and wait until all code has been generated in your custom
 * symbol processor in multiple rounds, e.g.
 * ```
 * @ContributingAnnotation
 * @Target(CLASS)
 * annotation class ContributesCustomCode
 * ```
 */
@Target(ANNOTATION_CLASS)
public annotation class ContributingAnnotation
