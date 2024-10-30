package software.amazon.lastmile.kotlin.inject.anvil.compat

/**
 * If set to true, warnings about unsupported parameters on Anvil annotations will be ignored.
 */
internal const val OPTION_IGNORE_DAGGER_ANVIL_UNSUPPORTED_PARAM_WARNINGS =
    "kotlin-inject-anvil-ignore-dagger-anvil-unsupported-param-warnings"

internal fun createUnsupportedParamMessage(
    annotation: String,
    param: String,
): String = "Unsupported parameters on @$annotation will be ignored: $param"
