package software.amazon.lastmile.kotlin.inject.anvil.gradle

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions

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
        // Note this doesn't work on JS/WASMJS yet due to
        // https://youtrack.jetbrains.com/issue/KT-71362
        val uniqueModuleName =
            project.findProperty("POM_ARTIFACT_ID")?.toString()?.let { artifactId ->
                "kotlin-inject-anvil-$artifactId"
            }
        with(kotlin) {
            androidTarget()

            iosArm64()
            iosSimulatorArm64()
            iosX64()

            js {
                moduleName = uniqueModuleName
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
                moduleName = uniqueModuleName
                browser()
            }

            watchosArm32()
            watchosArm64()
            watchosSimulatorArm64()
            watchosX64()

            applyDefaultHierarchyTemplate()
        }

        // Ensure a unique module name for each published artifact
        uniqueModuleName?.let { moduleName ->
            kotlin.targets.configureEach { target ->
                target.compilations.configureEach { compilation ->
                    compilation
                        .compilerOptions
                        .options
                        .let {
                            when (it) {
                                is KotlinNativeCompilerOptions -> {
                                    it.moduleName.set(moduleName)
                                }

                                is KotlinJvmCompilerOptions -> {
                                    it.moduleName.set(moduleName)
                                }

                                is KotlinJsCompilerOptions -> {
                                    it.moduleName.set(moduleName)
                                }
                            }
                        }
                }
            }
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
            // TODO revisit enabling after https://youtrack.jetbrains.com/issue/KT-71362 is fixed
            klib.enabled = false
        }
    }
}
