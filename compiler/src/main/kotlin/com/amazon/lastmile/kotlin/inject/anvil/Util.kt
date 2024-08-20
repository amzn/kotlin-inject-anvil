package com.amazon.lastmile.kotlin.inject.anvil

import com.amazon.lastmile.kotlin.inject.anvil.internal.Origin
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.Locale

/**
 * The package in which code is generated that should be picked up during the merging phase.
 */
internal const val LOOKUP_PACKAGE = "amazon.lastmile.inject"

internal fun String.decapitalize(): String = replaceFirstChar { it.lowercase(Locale.US) }
internal fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
}

internal fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addOriginAnnotation(
    clazz: KSClassDeclaration,
): T = addAnnotation(
    AnnotationSpec.builder(Origin::class)
        .addMember("value = %T::class", clazz.toClassName())
        .build(),
)
