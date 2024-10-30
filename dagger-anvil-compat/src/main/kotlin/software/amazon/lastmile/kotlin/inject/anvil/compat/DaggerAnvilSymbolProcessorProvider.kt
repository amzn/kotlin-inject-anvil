package software.amazon.lastmile.kotlin.inject.anvil.compat

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import software.amazon.lastmile.kotlin.inject.anvil.CompositeSymbolProcessor
import software.amazon.lastmile.kotlin.inject.anvil.compat.processor.DaggerAnvilContributesBindingProcessor
import software.amazon.lastmile.kotlin.inject.anvil.compat.processor.DaggerAnvilContributesToProcessor

/**
 * Entry point for KSP to pick up our [SymbolProcessor].
 */
@AutoService(SymbolProcessorProvider::class)
@Suppress("unused")
class DaggerAnvilSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return CompositeSymbolProcessor(
            symbolProcessors = listOf(
                DaggerAnvilContributesToProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                    options = environment.options,
                ),
                DaggerAnvilContributesBindingProcessor(
                    codeGenerator = environment.codeGenerator,
                    logger = environment.logger,
                    options = environment.options,
                ),
            ),
        )
    }
}
