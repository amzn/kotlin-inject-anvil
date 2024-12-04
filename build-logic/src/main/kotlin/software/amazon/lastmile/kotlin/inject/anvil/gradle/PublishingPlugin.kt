package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

internal class PublishingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(target.libs.findPlugin("maven-publish").get().get().pluginId)
    }
}
