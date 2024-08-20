@file:OptIn(ExperimentalCompilerApi::class)

package com.amazon.lastmile.kotlin.inject.anvil

import com.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import com.tschuchort.compiletesting.JvmCompilationResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

internal val JvmCompilationResult.componentInterface: Class<*>
    get() = classLoader.loadClass("com.amazon.test.ComponentInterface")

internal val Class<*>.inner: Class<*>
    get() = classes.single { it.simpleName == "Inner" }

internal val Class<*>.origin: Class<*>
    get() = getAnnotation(Origin::class.java).value.java

internal val Class<*>.generatedComponent: Class<*>
    get() = classLoader.loadClass(
        "$LOOKUP_PACKAGE." +
            canonicalName.split(".").joinToString(separator = "") { it.capitalize() },
    )

internal fun <T : Any> Class<*>.newComponent(): T {
    @Suppress("UNCHECKED_CAST")
    return classLoader.loadClass("$packageName.Inject$simpleName")
        .getDeclaredConstructor()
        .newInstance() as T
}

@Language("kotlin")
internal val otherScopeSource = """
    package com.amazon.test
    
    import me.tatarka.inject.annotations.Scope

    @Scope
    annotation class OtherScope

    @Scope
    annotation class OtherScope2
"""
