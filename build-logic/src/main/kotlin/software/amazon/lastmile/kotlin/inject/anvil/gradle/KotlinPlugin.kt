package software.amazon.lastmile.kotlin.inject.anvil.gradle

import guru.nidi.graphviz.engine.Format
import io.github.terrakok.KmpHierarchyConfig
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.io.File

internal open class KotlinPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureJavaCompile()
        target.configureKotlinCompile()
        target.configureKtLint()
        target.configureDetekt()
        target.configureHierarchyPlugin()
    }

    private fun Project.configureJavaCompile() {
        tasks.withType(JavaCompile::class.java).configureEach {
            it.sourceCompatibility = javaVersion.majorVersion
            it.targetCompatibility = javaVersion.majorVersion
        }
    }

    private fun Project.configureKotlinCompile() {
        tasks.withType(KotlinJvmCompile::class.java).configureEach {
            it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            it.compilerOptions.allWarningsAsErrors.set(ci)
        }
    }

    private fun Project.configureKtLint() {
        plugins.apply(libs.findPlugin("ktlint").get().get().pluginId)

        val ktlint = extensions.getByType(KtlintExtension::class.java)
        ktlint.filter { pattern ->
            pattern.exclude { fileTree ->
                fileTree.file.path.contains("generated/")
            }
        }
        ktlint.version.set(libs.findVersion("ktlint.binary").get().requiredVersion)
    }

    private fun Project.configureDetekt() {
        plugins.apply(libs.findPlugin("detekt").get().get().pluginId)

        fun SourceTask.configureDefaultDetektTask() {
            // The :detekt task in a multiplatform project doesn't do anything, it has no
            // sources configured. Instead, the Detekt plugin creates a Gradle task for each
            // source set, which then need to be wired manually to the 'release' task. This is
            // annoying and tedious.
            //
            // We make the default :detekt task analyze all .kt files, which is faster,
            // because only a single task runs, and we avoid all the wiring.
            setSource(layout.files("src"))
            exclude("**/api/**")
            exclude("**/build/**")
            exclude("**/detekt/**")
        }

        tasks.withType(Detekt::class.java).configureEach { detektTask ->
            detektTask.jvmTarget = javaVersion.toString()
            if (detektTask.name == "detekt") {
                detektTask.configureDefaultDetektTask()
            }
        }
        tasks.withType(DetektCreateBaselineTask::class.java).configureEach { detektTask ->
            detektTask.jvmTarget = javaVersion.toString()
            if (detektTask.name == "detektBaseline") {
                detektTask.configureDefaultDetektTask()
            }
        }

        extensions.getByType(DetektExtension::class.java).run {
            baseline = file("detekt-baseline.xml")
            config.from(File(rootDir, "gradle/detekt-config.yml"))
            buildUponDefaultConfig = true
        }
    }

    private fun Project.configureHierarchyPlugin() {
        plugins.withId(libs.findPlugin("kotlin.multiplatform").get().get().pluginId) {
            plugins.apply(libs.findPlugin("kotlin.hierarchy").get().get().pluginId)

            (extensions.getByType(KotlinMultiplatformExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(KmpHierarchyConfig::class.java)
                .run {
                    formats(Format.PNG, Format.SVG)
                    withTestHierarchy = true
                }
        }
    }
}
