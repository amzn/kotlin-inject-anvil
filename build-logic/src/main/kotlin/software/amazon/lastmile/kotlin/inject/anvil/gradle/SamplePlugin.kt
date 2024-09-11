package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

open class SamplePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)

        if (target.path == ":sample:app") {
            target.plugins.apply(target.libs.findPlugin("android.app").get().get().pluginId)
        } else {
            target.plugins.apply(target.libs.findPlugin("android.library").get().get().pluginId)
        }
        target.plugins.apply(AndroidPlugin::class.java)

        target.plugins.apply(target.libs.findPlugin("kotlin.multiplatform").get().get().pluginId)
        target.plugins.apply(KotlinPlugin::class.java)
    }
}
