package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation

/**
 * Generates the code for [ContributesTo].
 *
 * In the lookup package [LOOKUP_PACKAGE] a new interface is generated extending the contributed
 * interface. To avoid name clashes the package name of the original interface is encoded in the
 * interface name. E.g.
 * ```
 * package software.amazon.test
 *
 * @ContributesTo(AppScope::class)
 * @SingleIn(AppScope::class)
 * interface ComponentInterface
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @Origin(ComponentInterface::class)
 * interface SoftwareAmazonTestComponentInterface : ComponentInterface
 * ```
 */
internal class ContributesToProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesTo::class)
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsInterface(it)
                checkIsPublic(it)
                checkHasScope(it)
            }
            .forEach {
                generateComponentInterface(it)
            }

        return emptyList()
    }

    private fun generateComponentInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addSuperinterface(clazz.toClassName())
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }
}
