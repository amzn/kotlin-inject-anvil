@file:OptIn(KspExperimental::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.ContextAware
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.OPTION_CONTRIBUTING_ANNOTATIONS
import software.amazon.lastmile.kotlin.inject.anvil.extend.ContributingAnnotation
import software.amazon.lastmile.kotlin.inject.anvil.internal.Subcomponent
import software.amazon.lastmile.kotlin.inject.anvil.requireQualifiedName

/**
 * Generates the code for [MergeComponent].
 *
 * This processor waits until no more code will be generated in [LOOKUP_PACKAGE]. When this happens
 * it'll generate a new interface that extends all component interfaces in [LOOKUP_PACKAGE] with
 * the same scope. The original kotlin-inject then can extend this generated interface in order
 * for kotlin-inject to generate all necessary code. E.g.
 * ```
 * package software.amazon.test
 *
 * @Component
 * @MergeComponent(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class TestComponent : TestComponentMerged
 * ```
 * Will generate:
 * ```
 * package software.amazon.test
 *
 * interface TestComponentMerged : MergedSuperTypes
 * ```
 */
internal class MergeComponentProcessor(
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
    private val contributesSubcomponentProcessor: ContributesSubcomponentProcessor,
    options: Map<String, String>,
) : SymbolProcessor, ContextAware {

    private val defaultContributingAnnotations = buildSet {
        addAll(
            sequenceOf(
                ContributesTo::class,
                com.squareup.anvil.annotations.ContributesTo::class,
                ContributesBinding::class,
                ContributesSubcomponent::class,
                ContributesSubcomponent.Factory::class,
                ContributingAnnotation::class,
            )
                .map { it.requireQualifiedName() },
        )

        options[OPTION_CONTRIBUTING_ANNOTATIONS]?.let {
            addAll(
                it.splitToSequence(':')
                    .map { it.trim() }
                    .filterNot { it.isBlank() },
            )
        }
    }

    private val processedComponents = mutableSetOf<String>()

    @Suppress("ReturnCount")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // getSymbolsWithAnnotation() keeps returning duplicate elements or already
        // processed elements. In particular this happens in multi round scenarios with
        // deferred elements.
        //
        // Filter duplicates by comparing their qualified name. Keep track of which components
        // have been processed already and remove them if necessary through the
        // processedComponents set.
        val componentsToMerge = resolver
            .getSymbolsWithAnnotation(MergeComponent::class)
            .filterIsInstance<KSClassDeclaration>()
            .distinctBy { it.requireQualifiedName() }
            .filter { it.requireQualifiedName() !in processedComponents }
            .filter { it.isAnnotationPresent(Component::class) }
            .onEach {
                checkSuperTypeDeclared(it)
            }
            .toList()

        // Nothing to do.
        if (componentsToMerge.isEmpty()) {
            return emptyList()
        }

        val contributingAnnotations = defaultContributingAnnotations
            .asSequence()
            .plus(
                resolver.getDeclarationsFromPackage(LOOKUP_PACKAGE)
                    .filterIsInstance<KSPropertyDeclaration>()
                    .mapNotNull { property ->
                        // This gives us KClass<Abc>
                        property.type.resolve()
                            .arguments
                            .singleOrNull()
                            ?.type
                            // This gives us Abc
                            ?.resolve()
                            ?.declaration as? KSClassDeclaration
                    }
                    .map { it.requireQualifiedName() },
            )
            .distinct() // Save duplicate lookups

        // We will generate more component interfaces in our look up package in this round. Wait
        // for the next round before merging.
        if (hasNewContributionInNextRound(resolver, contributingAnnotations)) {
            return componentsToMerge.toList()
        }

        componentsToMerge.forEach {
            generateComponentInterface(resolver, it)
            processedComponents += it.requireQualifiedName()
        }

        return emptyList()
    }

    private fun generateComponentInterface(
        resolver: Resolver,
        clazz: KSClassDeclaration,
    ) {
        val className = ClassName(
            packageName = clazz.packageName.asString(),
            simpleNames = listOf(clazz.mergedClassName),
        )

        val scope = clazz.scope()
        val excludes = getExcludes(clazz)
        val excludeNames = excludes.map { it.requireQualifiedName() }

        val componentInterfaces = resolver.getDeclarationsFromPackage(LOOKUP_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .filter { contributedInterface ->
                val originChain = contributedInterface.originChain().toList()

                // Check that at least one of the scopes in the chain is matching the target
                // scope.
                val isSameScope = originChain.any { origin ->
                    origin.scopeOrNull() == scope
                }
                if (!isSameScope) {
                    return@filter false
                }

                // The scope matches, now check that none of the classes in the chain were
                // excluded.
                originChain.all { origin ->
                    origin.requireQualifiedName() !in excludeNames
                }
            }
            .filter {
                !it.isAnnotationPresent(Subcomponent::class) ||
                    it.contributedSubcomponent().requireQualifiedName() !in excludeNames
            }
            .toList()

        val generatedSubcomponents = contributesSubcomponentProcessor.generateFinalComponents(
            parentScopeComponent = clazz,
            componentInterfaces = componentInterfaces,
        )

        val fileSpec = FileSpec.builder(className)
            .addType(
                TypeSpec
                    .interfaceBuilder(className)
                    .addSuperinterfaces(
                        generatedSubcomponents + componentInterfaces.map { it.toClassName() },
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            aggregating = true,
            originatingKSFiles = buildSet {
                add(clazz.requireContainingFile())
                addAll(componentInterfaces.mapNotNull { it.containingFile })
                addAll(excludes.mapNotNull { it.containingFile })
            },
        )
    }

    private fun hasNewContributionInNextRound(
        resolver: Resolver,
        contributingAnnotations: Sequence<String>,
    ): Boolean {
        return contributingAnnotations
            .flatMap {
                resolver.getNewSymbolsWithAnnotation(it)
            }
            .any()
    }

    private fun Resolver.getNewSymbolsWithAnnotation(annotation: String): Sequence<KSAnnotated> {
        val newFiles = getNewFiles().toSet()
        return getSymbolsWithAnnotation(annotation)
            .filter { it.containingFile in newFiles }
    }

    private fun getExcludes(clazz: KSClassDeclaration): List<KSClassDeclaration> {
        val mergeAnnotation = clazz.findAnnotation(MergeComponent::class)
        val argument = mergeAnnotation.arguments.firstOrNull { it.name?.asString() == "exclude" }
            ?: mergeAnnotation.arguments.firstOrNull()

        @Suppress("UNCHECKED_CAST")
        return (argument?.value as? List<KSType>)
            ?.map { it.declaration as KSClassDeclaration }
            ?: emptyList()
    }

    private fun KSClassDeclaration.originChain(): Sequence<KSClassDeclaration> {
        return generateSequence(origin()) { clazz ->
            clazz.originOrNull()
        }
    }

    private fun checkSuperTypeDeclared(clazz: KSClassDeclaration) {
        check(clazz.superTypes.map { it.toString() }.any { it == clazz.mergedClassName }, clazz) {
            "${clazz.simpleName.asString()} is annotated with @MergeComponent and @Component. " +
                "It's required to add ${clazz.mergedClassName} as super type to " +
                "${clazz.simpleName.asString()}. If you don't want to add the super manually, " +
                "then you must remove the @Component annotation."
        }
    }
}
