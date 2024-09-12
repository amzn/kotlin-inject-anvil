package software.amazon.lastmile.kotlin.inject.anvil.gradle

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

open class LibraryKmpPlugin : Plugin<Project> {

    private val Project.kotlin: KotlinMultiplatformExtension
        get() = extensions.getByType(KotlinMultiplatformExtension::class.java)

    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)

        target.plugins.apply(target.libs.findPlugin("android.library").get().get().pluginId)
        target.plugins.apply(AndroidPlugin::class.java)

        target.plugins.apply(target.libs.findPlugin("kotlin.multiplatform").get().get().pluginId)
        target.plugins.apply(KotlinPlugin::class.java)
        target.configureExplicitApi()
        target.enableExpectActualClasses()
        target.configureKmp()
        target.addExtraSourceSets()

        target.plugins.apply(PublishingPlugin::class.java)

        target.configureBinaryCompatibilityValidator()
    }

    private fun Project.configureExplicitApi() {
        kotlin.explicitApi()
    }

    private fun Project.enableExpectActualClasses() {
        kotlin.targets.configureEach { target ->
            target.compilations.configureEach { compilation ->
                compilation.compilerOptions.options.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    private fun Project.configureKmp() {
        with(kotlin) {
            androidTarget()

            iosArm64()
            iosSimulatorArm64()
            iosX64()

            js {
                browser()
            }

            jvm()

            linuxArm64()
            linuxX64()

            macosArm64()
            macosX64()

            tvosArm64()
            tvosSimulatorArm64()
            tvosX64()

            wasmJs {
                browser()
            }

            watchosArm32()
            watchosArm64()
            watchosSimulatorArm64()
            watchosX64()

            applyDefaultHierarchyTemplate()
        }
    }

    private fun Project.addExtraSourceSets() {
        val commonMain = kotlin.sourceSets.getByName("commonMain")

        val jvmAndAndroid = kotlin.sourceSets.create("jvmAndAndroid")
        jvmAndAndroid.dependsOn(commonMain)

        kotlin.sourceSets.named("jvmMain").configure {
            it.dependsOn(jvmAndAndroid)
        }
        kotlin.sourceSets.named("androidMain").configure {
            it.dependsOn(jvmAndAndroid)
        }

        val nonJvmAndAndroid = kotlin.sourceSets.create("nonJvmAndAndroid")
        nonJvmAndAndroid.dependsOn(commonMain)

        kotlin.sourceSets.named("nativeMain").configure {
            it.dependsOn(nonJvmAndAndroid)
        }
        kotlin.sourceSets.named("jsMain").configure {
            it.dependsOn(nonJvmAndAndroid)
        }
        kotlin.sourceSets.named("wasmJsMain").configure {
            it.dependsOn(nonJvmAndAndroid)
        }
    }

    @OptIn(ExperimentalBCVApi::class)
    private fun Project.configureBinaryCompatibilityValidator() {
        plugins.apply(libs.findPlugin("kotlinx.binaryCompatibilityValidator").get().get().pluginId)

        with(extensions.getByType(ApiValidationExtension::class.java)) {
            klib.enabled = true
        }
    }
}
