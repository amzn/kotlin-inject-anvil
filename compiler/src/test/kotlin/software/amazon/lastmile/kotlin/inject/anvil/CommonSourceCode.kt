@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil

import com.tschuchort.compiletesting.JvmCompilationResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal val JvmCompilationResult.componentInterface: Class<*>
    get() = classLoader.loadClass("software.amazon.test.ComponentInterface")

internal val Class<*>.inner: Class<*>
    get() = classes.single { it.simpleName == "Inner" }

internal val Class<*>.origin: Class<*>
    get() = getAnnotation(Origin::class.java).value.java

internal val Class<*>.generatedComponent: Class<*>
    get() = classLoader.loadClass(
        "$LOOKUP_PACKAGE." +
            canonicalName.split(".").joinToString(separator = "") { it.capitalize() },
    )

internal val JvmCompilationResult.contributesRenderer: Class<*>
    get() = classLoader.loadClass("software.amazon.test.ContributesRenderer")

internal fun <T : Any> Class<*>.newComponent(): T {
    @Suppress("UNCHECKED_CAST")
    return classLoader.loadClass("$packageName.Inject$simpleName")
        .getDeclaredConstructor()
        .newInstance() as T
}

internal val Class<*>.mergedComponent: Class<*>
    get() = classLoader.loadClass(
        "$packageName." +
            canonicalName.substring(packageName.length + 1).replace(".", "") +
            "Merged",
    )

private val Class<*>.propertyClass: Class<*>
    get() = classLoader.loadClass(
        "$LOOKUP_PACKAGE." +
            canonicalName.split(".").joinToString(separator = "") { it.capitalize() } +
            "Kt",
    )

internal val Class<*>.propertyMethodGetter: Method
    get() = propertyClass.methods
        .filter { Modifier.isStatic(it.modifiers) }
        .single { '$' !in it.name }

internal val Class<*>.propertyAnnotations: Array<out Annotation>
    get() = propertyClass.methods
        .filter { Modifier.isStatic(it.modifiers) }
        .single { it.name.endsWith("\$annotations") }
        .annotations

@Language("kotlin")
internal val otherScopeSource = """
    package software.amazon.test
    
    import me.tatarka.inject.annotations.Scope

    @Scope
    annotation class OtherScope

    @Scope
    annotation class OtherScope2
"""
