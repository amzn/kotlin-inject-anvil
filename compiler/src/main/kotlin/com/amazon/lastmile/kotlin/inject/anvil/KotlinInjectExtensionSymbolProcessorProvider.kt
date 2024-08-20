package com.amazon.lastmile.kotlin.inject.anvil

import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesSubcomponentFactoryProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesSubcomponentProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesToProcessor
import com.amazon.lastmile.kotlin.inject.anvil.processor.MergeComponentProcessor
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
        return CompositeSymbolProcessor(
            ContributesToProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
            ),
            ContributesBindingProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
            ),
            ContributesSubcomponentFactoryProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
            ),
            MergeComponentProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
                contributesSubcomponentProcessor = ContributesSubcomponentProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                ),
                contributingAnnotations = listOf(
                    ContributesTo::class,
                    ContributesBinding::class,
                    ContributesSubcomponent::class,
                    ContributesSubcomponent.Factory::class,
                ),
            ),
        )
    }
}
