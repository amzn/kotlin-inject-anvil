package software.amazon.lastmile.kotlin.inject.anvil.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class PublishingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(target.libs.findPlugin("maven-publish").get().get().pluginId)
        target.configurePublishing()
    }

    private fun Project.configurePublishing() {
        val extension = extensions.getByType(MavenPublishBaseExtension::class.java)
        extension.publishToMavenCentral(SonatypeHost("https://aws.oss.sonatype.org"))
    }
}
