package software.amazon.lastmile.kotlin.inject.anvil.processor.extend

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.decapitalize
import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
import kotlin.reflect.KClass

/**
 * Generates the code for [ContributingAnnotation].
 *
 * In the lookup package [LOOKUP_PACKAGE] a new global property pointing to the annotation
 * class is generated. To avoid name clashes the package name of the original annotation
 * class is encoded in the property name, e.g.
 * ```
 * package software.amazon.test
 *
 * @ContributingAnnotation
 * annotation class ContributesRenderer
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @Origin(ContributesRenderer::class)
 * public val softwareAmazonTestContributesRenderer: KClass<ContributesRenderer>
 *     = ContributesRenderer::class
 * ```
 */
internal class ContributingAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributingAnnotation::class)
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsPublic(it) {
                    "Contributing annotations must be public."
                }
            }
            .forEach {
                generateProperty(it)
            }

        return emptyList()
    }

    private fun generateProperty(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val fileSpec = FileSpec.builder(componentClassName)
            .addProperty(
                PropertySpec
                    .builder(
                        name = clazz.safeClassName.decapitalize(),
                        type = KClass::class.asClassName().parameterizedBy(clazz.toClassName()),
                        modifiers = setOf(KModifier.PRIVATE),
                    )
                    .initializer("%T::class", clazz.toClassName())
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }
}
