package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
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
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.internal.Subcomponent

/**
 * Generates the code for [ContributesSubcomponent.Factory].
 *
 * In the lookup package [LOOKUP_PACKAGE] a new interface is generated extending the contributed
 * interface. To avoid name clashes the package name of the original interface is encoded in the
 * interface name. This is very similar to [ContributesTo] with the key differences that there
 * are more strict checks and that [Subcomponent] is added as marker.
 * ```
 * package software.amazon.test
 *
 * @ContributesSubcomponent(LoggedInScope::class)
 * @SingleIn(AppScope::class)
 * interface Subcomponent {
 *     @ContributesSubcomponent.Factory(AppScope::class)
 *     interface Factory {
 *         fun createSubcomponent(): Subcomponent
 *     }
 * }
 * ```
 * Will generate:
 * ```
 * package $LOOKUP_PACKAGE
 *
 * @Origin(Subcomponent.Factory::class)
 * @Subcomponent
 * interface SoftwareAmazonTestSubcomponentFactory : Subcomponent.Factory
 * ```
 */
@OptIn(KspExperimental::class)
internal class ContributesSubcomponentFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesSubcomponent.Factory::class)
            .filterIsInstance<KSClassDeclaration>()
            .onEach {
                checkIsInterface(it)
                checkIsPublic(it)
                checkInnerClass(it)
                checkSingleFunction(it)
                checkHasScope(it)
            }
            .forEach {
                generateComponentInterfaceForFactory(it)
            }

        resolver
            .getSymbolsWithAnnotation(ContributesSubcomponent::class)
            .filterIsInstance<KSClassDeclaration>()
            .forEach {
                checkIsInterface(it) {
                    "Only interfaces can be contributed. If you have parameters on your " +
                        "abstract class, then move them to the factory. See " +
                        "@ContributesSubcomponent for more details."
                }
                checkHasFactoryInnerClass(it)
            }

        return emptyList()
    }

    private fun generateComponentInterfaceForFactory(factory: KSClassDeclaration) {
        val componentClassName = ClassName(LOOKUP_PACKAGE, factory.safeClassName)

        val fileSpec = FileSpec.builder(componentClassName)
            .addType(
                TypeSpec
                    .interfaceBuilder(componentClassName)
                    .addOriginatingKSFile(factory.requireContainingFile())
                    .addOriginAnnotation(factory)
                    .addAnnotation(Subcomponent::class)
                    .addSuperinterface(factory.toClassName())
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun checkInnerClass(factory: KSClassDeclaration) {
        val subcomponent =
            requireNotNull(factory.parentDeclaration as? KSClassDeclaration, factory) {
                "Factory interfaces must be inner classes of the contributed subcomponent."
            }

        check(subcomponent.isAnnotationPresent(ContributesSubcomponent::class), factory) {
            "Factory interfaces must be inner classes of the contributed subcomponent, which " +
                "need to be annotated with @ContributesSubcomponent."
        }

        requireKotlinInjectScope(subcomponent)
    }

    private fun checkSingleFunction(factory: KSClassDeclaration) {
        val functions = factory.factoryFunctions().toList()
        check(functions.size == 1, factory) {
            "Factory interfaces must contain exactly one function with the subcomponent as " +
                "return type."
        }

        val returnType = requireNotNull(functions.single().returnType, factory) {
            "Factory interfaces must contain exactly one function with the subcomponent as " +
                "return type."
        }

        check(
            (returnType.resolve().declaration as? KSClassDeclaration)?.requireQualifiedName() ==
                factory.subcomponent.requireQualifiedName(),
            factory,
        ) {
            "Factory interfaces must contain exactly one function with the subcomponent as " +
                "return type."
        }
    }

    private fun checkHasFactoryInnerClass(subcomponent: KSClassDeclaration) {
        val factory = subcomponent.declarations
            .filterIsInstance<KSClassDeclaration>()
            .singleOrNull {
                it.isAnnotationPresent(ContributesSubcomponent.Factory::class)
            }

        check(factory != null, subcomponent) {
            "Contributed subcomponent must have exactly one inner interface annotated " +
                "with @ContributesSubcomponent.Factory."
        }
    }

    private val KSClassDeclaration.subcomponent: KSClassDeclaration
        get() = parentDeclaration as KSClassDeclaration
}
