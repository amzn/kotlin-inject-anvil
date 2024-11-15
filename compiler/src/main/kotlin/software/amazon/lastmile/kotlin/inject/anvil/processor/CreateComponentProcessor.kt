@file:OptIn(KspExperimental::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ACTUAL
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName
import kotlin.reflect.KClass

/**
 * This processor will generate a function to make instantiating a generated kotlin-inject
 * component easier. The function delegates the call to the final kotlin-inject component.
 * ```
 * package software.amazon.test
 *
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class TestComponent(
 *     @get:Provides val string: String,
 * )
 *
 * @CreateComponent
 * expect fun createTestComponent(string: String): TestComponent
 * ```
 * Will generate:
 * ```
 * actual fun createTestComponent(string: String): TestComponent {
 *     return KotlinInjectTestComponent::class.create(string)
 * }
 * ```
 */
internal class CreateComponentProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, ContextAware {

    private val kclassFqName = KClass::class.requireQualifiedName()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(MergeComponent.CreateComponent::class)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter {
                // We copy all annotations from the expect function, including @CreateComponent,
                // to the actual functions. Without doing so the Kotlin compiler would print a
                // warning.
                //
                // Without this filter we'd try to process the generated actual function, which
                // leads to errors.
                Modifier.ACTUAL !in it.modifiers
            }
            .onEach { function ->
                checkIsPublic(function) {
                    "Factory functions for components annotated with `@CreateComponent` must be public."
                }
                checkKotlinInjectComponentWillBeGenerated(function)
                checkReceiverType(function)
                checkArguments(function)
                checkIsExpectFunction(function)
            }
            .forEach {
                generateActualFunction(it)
            }

        return emptyList()
    }

    private fun generateActualFunction(function: KSFunctionDeclaration) {
        val component = (function.requireReturnType().resolve().declaration as KSClassDeclaration)
            .toClassName()
        val generatedComponent = component.peerClass("KotlinInject${component.simpleName}")

        val parametersAsSpec = function.parameters.map {
            ParameterSpec
                .builder(
                    name = it.requireName(),
                    type = it.type.toTypeName(),
                )
                .build()
        }

        val fileSpec = FileSpec
            .builder(
                packageName = function.packageName.asString(),
                fileName = function.requireContainingFile().fileName.substringBefore(".kt") +
                    "CreateComponent",
            )
            .addFunction(
                FunSpec
                    .builder(function.simpleName.asString())
                    .apply {
                        if (function.extensionReceiver != null) {
                            receiver(
                                KClass::class.asTypeName().parameterizedBy(component),
                            )
                        }
                    }
                    .addAnnotations(function.annotations.map { it.toAnnotationSpec() }.toList())
                    .addModifiers(ACTUAL)
                    .addParameters(parametersAsSpec)
                    .returns(component)
                    .addStatement(
                        "return %T::class.create(${parametersAsSpec.joinToString { it.name }})",
                        generatedComponent,
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun checkKotlinInjectComponentWillBeGenerated(function: KSFunctionDeclaration) {
        val componentClass = function.requireReturnType().resolve().declaration
        check(componentClass.isAnnotationPresent(MergeComponent::class), function) {
            "The return type ${componentClass.requireQualifiedName()} is not annotated with `@MergeComponent`."
        }
        check(!componentClass.isAnnotationPresent(Component::class), function) {
            "The return type ${componentClass.requireQualifiedName()} should not be annotated " +
                "with `@Component`. In this scenario use the built-in annotations from " +
                "kotlin-inject itself."
        }
    }

    private fun checkReceiverType(function: KSFunctionDeclaration) {
        val receiverType =
            function.extensionReceiver?.resolve()?.declaration?.requireQualifiedName() ?: return
        check(receiverType == kclassFqName, function) {
            "Only a receiver type on KClass<YourComponent> is supported."
        }

        val receiverArgument =
            function.extensionReceiver?.resolve()?.arguments?.singleOrNull()?.type
                ?.resolve()?.declaration?.requireQualifiedName()
        val returnType = function.requireReturnType().resolve().declaration.requireQualifiedName()
        check(receiverArgument == returnType, function) {
            "Only a receiver type on KClass<YourComponent> is supported. The argument was different."
        }
    }

    private fun checkArguments(function: KSFunctionDeclaration) {
        val componentParameters =
            (function.requireReturnType().resolve().declaration as? KSClassDeclaration)
                ?.primaryConstructor?.parameters ?: emptyList()

        check(componentParameters.size == function.parameters.size, function) {
            "The number of arguments for the function doesn't match the number of arguments of the component."
        }
    }

    private fun checkIsExpectFunction(function: KSFunctionDeclaration) {
        check(Modifier.EXPECT in function.modifiers, function) {
            "Only expect functions can be annotated with @MergeComponent.CreateComponent. " +
                "In non-common Kotlin Multiplatform code use the generated `create` extension " +
                "function on the class object: YourComponent.create(..)."
        }
    }

    private fun KSFunctionDeclaration.requireReturnType(): KSTypeReference {
        return requireNotNull(returnType, this) {
            "Couldn't determine return type for $this"
        }
    }
}
