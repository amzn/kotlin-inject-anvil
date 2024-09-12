package software.amazon.lastmile.kotlin.inject.anvil.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import java.net.URI

internal class BasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.addRepositories()
        target.runTestsInHeadlessMode()
    }

    private fun Project.addRepositories() {
        with(repositories) {
            mavenCentral()
            google()
            maven {
                it.url = URI.create("https://oss.sonatype.org/content/repositories/snapshots/")
            }
        }
    }

    private fun Project.runTestsInHeadlessMode() {
        // Otherwise the java icon keeps popping up in the system tray while running tests.
        tasks.withType(Test::class.java).configureEach {
            it.systemProperty("java.awt.headless", "true")
        }
    }
}
