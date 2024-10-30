@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.test

import com.tschuchort.compiletesting.JvmCompilationResult
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.capitalize
import software.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import java.lang.reflect.Field
import java.lang.reflect.Modifier

val JvmCompilationResult.componentInterface: Class<*>
    get() = classLoader.loadClass("software.amazon.test.ComponentInterface")

val Class<*>.inner: Class<*>
    get() = classes.single { it.simpleName == "Inner" }

val Class<*>.origin: Class<*>
    get() = getAnnotation(Origin::class.java).value.java

val Class<*>.generatedComponent: Class<*>
    get() = classLoader.loadClass(
        "$LOOKUP_PACKAGE." +
            canonicalName.split(".").joinToString(separator = "") { it.capitalize() },
    )

val JvmCompilationResult.contributesRenderer: Class<*>
    get() = classLoader.loadClass("software.amazon.test.ContributesRenderer")

fun <T : Any> Class<*>.newComponent(vararg arguments: Any): T {
    @Suppress("UNCHECKED_CAST")
    return classLoader.loadClass("$packageName.Inject$simpleName")
        .getDeclaredConstructor(
            *arguments.map { arg ->
                arg::class.java.primitiveByWrapper ?: arg::class.java
            }.toTypedArray(),
        )
        .newInstance(*arguments) as T
}

val Class<*>.mergedComponent: Class<*>
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

val Class<*>.generatedProperty: Field
    get() = propertyClass.declaredFields.single().also { it.isAccessible = true }

val Class<*>.propertyAnnotations: Array<out Annotation>
    get() = propertyClass.declaredMethods
        .filter { Modifier.isStatic(it.modifiers) }
        .single { it.name.endsWith("\$annotations") }
        .annotations
