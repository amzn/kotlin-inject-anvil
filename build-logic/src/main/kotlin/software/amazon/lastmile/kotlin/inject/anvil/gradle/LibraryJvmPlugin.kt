package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

open class LibraryJvmPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)
        target.plugins.apply(target.libs.findPlugin("kotlin.jvm").get().get().pluginId)
        target.plugins.apply(KotlinPlugin::class.java)
        target.plugins.apply(PublishingPlugin::class.java)
    }
}
