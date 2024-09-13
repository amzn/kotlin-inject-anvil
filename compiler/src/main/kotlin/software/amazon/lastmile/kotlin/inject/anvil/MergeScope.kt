package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * Represents the destination of contributed types and which types should be merged during
 * the merge phase. There is complexity to this problem, because `kotlin-inject` didn't
 * support parameters for scopes initially and our Anvil extensions added support for that. Later,
 * we started supporting parameters, which changed the API. E.g. one could use:
 * ```
 * @ContributesTo(AppScope::class)
 * interface ContributedComponentInterface
 *
 * @Component
 * @MergeComponent(AppScope::class)
 * interface MergedComponent
 * ```
 * Or the old way:
 * ```
 * @ContributesTo
 * @Singleton
 * interface ContributedComponentInterface
 *
 * @Component
 * @MergeComponent
 * @Singleton
 * interface MergedComponent
 * ```
 */
internal sealed class MergeScope {
    /**
     * The fully qualified name of the annotation used as scope, e.g.
     * ```
     * @ContributesTo
     * @Singleton
     * interface Abc
     * ```
     * Note that the annotation itself is annotated with `@Scope`.
     *
     * The value is `null`, when only a marker is used, e.g.
     * ```
     * @ContributesTo(AppScope::class)
     * interface Abc
     * ```
     *
     * If the `scope` parameter is used and the argument is annotated with `@Scope`, then
     * this value is non-null, e.g. for this:
     * ```
     * @ContributesBinding(scope = Singleton::class)
     * class Binding : SuperType
     * ```
     */
    abstract val annotationFqName: String?

    /**
     * A marker for a scope that isn't itself annotated with `@Scope`, e.g.
     * ```
     * @ContributesTo(AppScope::class)
     * interface Abc
     * ```
     *
     * The value is null, if no marker is used, e.g.
     * ```
     * @ContributesTo
     * @Singleton
     * interface Abc
     * ```
     *
     * The value is also null, when the `scope` parameter is used and the argument is annotated
     * with `@Scope`, e.g.
     * ```
     * @ContributesBinding(scope = Singleton::class)
     * class Binding : SuperType
     * ```
     */
    abstract val markerFqName: String?

    /**
     * A reference to the scope.
     *
     * [markerFqName] is preferred, because it allows us to decouple contributions from
     * kotlin-inject's scoping mechanism. E.g. imagine someone using `@Singleton` as a scope, and
     * they'd like to adopt kotlin-inject-anvil with `@ContributesTo(AppScope::class)`. Because we
     * prefer the marker, this would be supported.
     */
    val fqName: String get() = requireNotNull(markerFqName ?: annotationFqName)

    abstract fun toAnnotationSpec(): AnnotationSpec

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MergeScope) return false

        if (fqName != other.fqName) return false

        return true
    }

    override fun hashCode(): Int {
        return fqName.hashCode()
    }

    private class MarkerBasedMergeScope(
        override val annotationFqName: String,
        override val markerFqName: String?,
        private val ksAnnotation: KSAnnotation,
    ) : MergeScope() {
        override fun toAnnotationSpec(): AnnotationSpec {
            return ksAnnotation.toAnnotationSpec()
        }
    }

    private class AnnotationBasedMergeScope(
        override val annotationFqName: String?,
        override val markerFqName: String?,
        private val ksType: KSType,
    ) : MergeScope() {
        override fun toAnnotationSpec(): AnnotationSpec {
            return AnnotationSpec.builder(ksType.toClassName()).build()
        }
    }

    companion object {
        operator fun invoke(
            contextAware: ContextAware,
            annotationType: KSType?,
            markerType: KSType?,
        ): MergeScope {
            val nonNullType = contextAware.requireNotNull(markerType ?: annotationType, null) {
                "Couldn't determine scope. No scope annotation nor marker found."
            }

            return AnnotationBasedMergeScope(
                annotationFqName = annotationType?.declaration?.requireQualifiedName(contextAware),
                markerFqName = markerType?.declaration?.requireQualifiedName(contextAware),
                ksType = nonNullType,
            )
        }

        operator fun invoke(
            contextAware: ContextAware,
            ksAnnotation: KSAnnotation,
        ): MergeScope {
            return MarkerBasedMergeScope(
                annotationFqName = ksAnnotation.annotationType.resolve().declaration
                    .requireQualifiedName(contextAware),
                markerFqName = ksAnnotation.scopeParameter(contextAware)?.declaration
                    ?.requireQualifiedName(contextAware),
                ksAnnotation = ksAnnotation,
            )
        }
    }
}
