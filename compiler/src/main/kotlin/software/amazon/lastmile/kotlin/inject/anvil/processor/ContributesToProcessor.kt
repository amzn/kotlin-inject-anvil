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
import software.amazon.lastmile.kotlin.inject.anvil.OPTION_IGNORE_ANVIL_UNSUPPORTED_PARAM_WARNINGS
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
    options: Map<String, String>,
) : SymbolProcessor, ContextAware {

    private val ignoreUnsupportedParameters: Boolean by lazy {
        options[OPTION_IGNORE_ANVIL_UNSUPPORTED_PARAM_WARNINGS] == "true"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotations(
                ContributesTo::class,
                com.squareup.anvil.annotations.ContributesTo::class,
            )
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsInterface(it)
                checkIsPublic(it)
                checkHasScope(it)
                if (!ignoreUnsupportedParameters) {
                    warnIfUnsupportedParameters(it)
                }
            }
            .forEach {
                generateComponentInterface(it)
            }

        return emptyList()
    }

    private fun warnIfUnsupportedParameters(clazz: KSClassDeclaration) {
        val annotation = clazz.findAnnotationOrNull(
            com.squareup.anvil.annotations.ContributesTo::class,
        ) ?: return

        if (annotation.arguments.any { it.name?.asString() == "replaces" }) {
            logger.warn(createUnsupportedParamMessage("replaces"), annotation)
        }
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

    internal companion object {
        fun createUnsupportedParamMessage(param: String): String =
            "Unsupported parameters on @ContributesTo will be ignored: $param"
    }
}
