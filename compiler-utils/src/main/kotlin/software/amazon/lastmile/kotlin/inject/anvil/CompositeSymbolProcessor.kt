package software.amazon.lastmile.kotlin.inject.anvil

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * A [SymbolProcessor] that delegates to a collection of [SymbolProcessor]s.
 */
class CompositeSymbolProcessor(
    symbolProcessors: Collection<SymbolProcessor>,
) : SymbolProcessor {

    private val symbolProcessors = symbolProcessors.sortedBy { it::class.qualifiedName }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        return symbolProcessors.flatMap { it.process(resolver) }
    }

    override fun finish() {
        symbolProcessors.forEach { it.finish() }
    }

    override fun onError() {
        symbolProcessors.forEach { it.onError() }
    }
}
