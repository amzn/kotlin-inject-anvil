package software.amazon.lastmile.kotlin.inject.anvil.compat.processor

import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.compat.OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS
import software.amazon.lastmile.kotlin.inject.anvil.compat.createUnsupportedParamMessage

/**
 * Generates the code for [com.squareup.anvil.annotations.ContributesTo].
 *
 * In the lookup package [LOOKUP_PACKAGE] a new interface is generated extending the contributed
 * interface. To avoid name clashes the package name of the original interface is encoded in the
 * interface name. E.g.
 * ```
 * package software.amazon.test
 *
 * @com.squareup.anvil.annotations.ContributesTo(AppScope::class)
 * @SingleIn(AppScope::class)
 * interface ComponentInterface
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @Origin(ComponentInterface::class)
 * @software.amazon.lastmile.kotlin.inject.anvil.ContributesTo(AppScope::class)
 * interface SoftwareAmazonTestComponentInterface : ComponentInterface
 * ```
 */
internal class DaggerAnvilContributesToProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor, ContextAware {

    private val ignoreUnsupportedParameters: Boolean by lazy {
        options[OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS] == "true"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(ContributesTo::class)
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
                generateContributesToInterface(it)
            }
        return emptyList()
    }

    private fun generateContributesToInterface(clazz: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, clazz.safeClassName)

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addAnnotation(
                        AnnotationSpec.builder(
                            software.amazon.lastmile.kotlin.inject.anvil.ContributesTo::class,
                        )
                            .addMember("scope = %T::class", clazz.scope().type.toClassName())
                            .build(),
                    )
                    .addSuperinterface(clazz.toClassName())
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun warnIfUnsupportedParameters(clazz: KSClassDeclaration) {
        val annotation = clazz.findAnnotationOrNull(ContributesTo::class) ?: return

        val argument = annotation.arguments
            .firstOrNull { it.name?.asString() == "replaces" }
            ?.takeIf { !it.isDefault() }

        if (argument != null) {
            logger.warn(
                createUnsupportedParamMessage("ContributesTo", "replaces"),
                argument,
            )
        }
    }
}
