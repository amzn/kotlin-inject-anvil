package software.amazon.lastmile.kotlin.inject.anvil.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class AndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureDefaultAndroid()
    }

    private fun Project.configureDefaultAndroid() {
        plugins.withType(BasePlugin::class.java).configureEach {
            val android = extensions.getByType(BaseExtension::class.java)

            android.namespace =
                "software.amazon.lastmile.kotlin.inject.anvil${path.replace(':', '.')}"
            android.compileSdkVersion(
                libs.findVersion("android.compileSdk").get().requiredVersion.toInt(),
            )

            android.defaultConfig {
                it.setMinSdkVersion(
                    libs.findVersion("android.minSdk").get().requiredVersion.toInt(),
                )
                it.setTargetSdkVersion(
                    libs.findVersion("android.compileSdk").get().requiredVersion.toInt(),
                )
            }

            android.compileOptions {
                it.sourceCompatibility = javaVersion
                it.targetCompatibility = javaVersion
            }

            android.lintOptions {
                it.isWarningsAsErrors = true
                it.htmlReport = true
            }

            with(extensions.getByType(AndroidComponentsExtension::class.java)) {
                beforeVariants(selector().withBuildType("release")) { variant ->
                    (variant as HasUnitTestBuilder).enableUnitTest = false
                }
            }
        }
    }
}
