package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware

internal val ExtensionAware.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal val ExtensionAware.javaVersion: JavaVersion
    get() = JavaVersion.VERSION_11

internal val Project.ci: Boolean
    get() = providers.environmentVariable("CI").isPresent ||
        providers.gradleProperty("CI").isPresent
