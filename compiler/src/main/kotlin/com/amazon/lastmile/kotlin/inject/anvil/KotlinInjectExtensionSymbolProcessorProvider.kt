package com.amazon.lastmile.kotlin.inject.anvil

import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesSubcomponentFactoryProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesSubcomponentProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesToProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.MergeComponentProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.extend.ContributingAnnotationProcessor
import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Entry point for KSP to pick up our [SymbolProcessor].
 */
@AutoService(SymbolProcessorProvider::class)
@Suppress("unused")
class KotlinInjectExtensionSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        fun MutableSet<SymbolProcessor>.addIfEnabled(symbolProcessor: SymbolProcessor) {
            val disabled = environment.options[symbolProcessor::class.qualifiedName] == "disabled"
            if (!disabled) {
                add(symbolProcessor)
            } else {
                environment.logger
                    .info("Disabled kotlin-inject-anvil processor ${symbolProcessor::class}")
            }
        }

        val symbolProcessors = buildSet {
            addIfEnabled(
                ContributesToProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                ),
            )
            addIfEnabled(
                ContributesBindingProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                ),
            )
            addIfEnabled(
                ContributesSubcomponentFactoryProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                ),
            )
            addIfEnabled(
                MergeComponentProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                    contributesSubcomponentProcessor = ContributesSubcomponentProcessor(
                        codeGenerator = environment.codeGenerator,
                        logger = environment.logger,
                    ),
                ),
            )
            addIfEnabled(
                ContributingAnnotationProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                ),
            )
        }

        return CompositeSymbolProcessor(symbolProcessors)
    }
}
