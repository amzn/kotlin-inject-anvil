package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

open class RootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)
        target.logger.lifecycle("CI build: ${target.ci}")
    }
}
