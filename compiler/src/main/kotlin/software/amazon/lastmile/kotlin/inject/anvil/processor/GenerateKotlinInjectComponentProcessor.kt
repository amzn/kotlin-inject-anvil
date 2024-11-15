@file:OptIn(KspExperimental::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PROTECTED
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.addOriginAnnotation
import kotlin.reflect.KClass

/**
 * Generates the final kotlin-inject component when [MergeComponent] is found without the
 * [Component] annotation, e.g.
 * ```
 * package software.amazon.test
 *
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * interface TestComponent
 * ```
 * Will generate:
 * ```
 * package software.amazon.test
 *
 * @MergeComponent(AppScope::class)
 * @Component
 * @SingleIn(AppScope::class)
 * interface KotlinInjectTestComponent : KotlinInjectTestComponentMerged
 * ```
 *
 * Parameters are supported as well, e.g.
 * ```
 * package software.amazon.test
 *
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class TestComponent(
 *     @get:Provides val string: String,
 * )
 * ```
 * Will generate:
 * ```
 * package software.amazon.test
 *
 * @MergeComponent(AppScope::class)
 * @Component
 * @SingleIn(AppScope::class)
 * abstract class KotlinInjectTestComponent(string: String) : KotlinInjectTestComponentMerged(string)
 * ```
 *
 * This processor will also add a function to make instantiating the generated component easier.
 * The function delegates the call to the final kotlin-inject component. For the example above
 * the following function would be generated:
 * ```
 * fun KClass<TestComponent>.create(string: String): TestComponent {
 *     return KotlinInjectTestComponent::class.create(string)
 * }
 * ```
 */
internal class GenerateKotlinInjectComponentProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {

    private val processedComponents = mutableSetOf<String>()

    @Suppress("ReturnCount")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(MergeComponent::class)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.requireQualifiedName() !in processedComponents }
            .filter { !it.isAnnotationPresent(Component::class) }
            .onEach {
                checkNotPrivate(it)
                checkHasScope(it)
            }
            .forEach {
                generateKotlinInjectComponent(it)

                processedComponents += it.requireQualifiedName()
            }

        return emptyList()
    }

    @Suppress("LongMethod")
    private fun generateKotlinInjectComponent(clazz: KSClassDeclaration) {
        val className = ClassName(
            packageName = clazz.packageName.asString(),
            simpleNames = listOf("KotlinInject${clazz.innerClassNames()}"),
        )

        val isInterface = clazz.classKind == ClassKind.INTERFACE
        val parameters = clazz.primaryConstructor?.parameters ?: emptyList()
        val parametersAsSpec = parameters.map { parameter ->
            ParameterSpec
                .builder(
                    name = parameter.requireDelegateName(),
                    type = parameter.type.toTypeName(),
                )
                .addAnnotations(parameter.annotations.map { it.toAnnotationSpec() }.toList())
                .build()
        }

        val classBuilder = if (isInterface) {
            TypeSpec
                .interfaceBuilder(className)
                .addSuperinterface(clazz.toClassName())
                .addModifiers(clazz.getAccessModifier())
        } else {
            TypeSpec
                .classBuilder(className)
                .addModifiers(ABSTRACT)
                .addModifiers(clazz.getAccessModifier())
                .superclass(clazz.toClassName())
                .apply {
                    if (parameters.isNotEmpty()) {
                        primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameters(parametersAsSpec)
                                .build(),
                        )
                        addSuperclassConstructorParameter(
                            parameters.joinToString { it.requireDelegateName() },
                        )
                    }
                }
                .addProperties(
                    parameters
                        .filter { parameter ->
                            parameter.annotations.any { it.isKotlinInjectComponentAnnotation() }
                        }
                        .map { parameter ->
                            val name = parameter.requireDelegateName()
                            PropertySpec
                                .builder(name, parameter.type.toTypeName())
                                .initializer(name)
                                .build()
                        },
                )
        }

        val fileSpec = FileSpec.builder(className)
            .addType(
                classBuilder
                    .addOriginatingKSFile(clazz.requireContainingFile())
                    .addOriginAnnotation(clazz)
                    .addAnnotation(Component::class)
                    .addAnnotation(clazz.findAnnotation(MergeComponent::class).toAnnotationSpec())
                    .apply {
                        clazz.annotations
                            .filter { it.isKotlinInjectScopeAnnotation() }
                            .singleOrNull()
                            ?.toAnnotationSpec()
                            ?.let { addAnnotation(it) }
                    }
                    .addSuperinterface(
                        className.peerClass("KotlinInject${clazz.mergedClassName}"),
                    )
                    .addType(TypeSpec.companionObjectBuilder().build())
                    .build(),
            )
            .addFunction(
                FunSpec
                    .builder("create")
                    .receiver(
                        KClass::class.asTypeName().parameterizedBy(clazz.toClassName()),
                    )
                    .addParameters(parametersAsSpec)
                    .returns(clazz.toClassName())
                    .addStatement(
                        "return %T::class.create(${parametersAsSpec.joinToString { it.name }})",
                        className,
                    )
                    .addModifiers(clazz.getAccessModifier())
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    /**
     * Creates a custom name for parameters of the new Kotlin Inject interface
     * as to not require parameters to be open.
     */
    private fun KSValueParameter.requireDelegateName(): String {
        return "${this.requireName()}Delegate"
    }

    private fun KSClassDeclaration.getAccessModifier(): KModifier {
        return when (getVisibility()) {
            Visibility.PUBLIC -> PUBLIC
            Visibility.PRIVATE -> PRIVATE
            Visibility.INTERNAL -> INTERNAL
            Visibility.LOCAL,
            Visibility.JAVA_PACKAGE,
            Visibility.PROTECTED,
            -> PROTECTED
        }
    }
}
