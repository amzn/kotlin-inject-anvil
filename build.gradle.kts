import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.detekt) apply true
    alias(libs.plugins.ktlint) apply true
}

val ci by extra {
    providers.environmentVariable("CI").isPresent ||
        providers.gradleProperty("CI").isPresent
}

logger.lifecycle("CI build: $ci")

subprojects {
    val javaTarget = JvmTarget.JVM_11
    val javaVersion = JavaVersion.toVersion(javaTarget.target)

    plugins.withType<BasePlugin>().configureEach {
        val android = extensions.getByName("android") as BaseExtension

        android.compileSdkVersion(rootProject.libs.versions.android.compileSdk.get().toInt())

        android.defaultConfig {
            minSdk = rootProject.libs.versions.android.minSdk.get().toInt()
            targetSdk = rootProject.libs.versions.android.compileSdk.get().toInt()
        }

        android.compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        android.lintOptions {
            isWarningsAsErrors = true
            htmlReport = true
        }

        val androidComponents =
            extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>
        androidComponents.beforeVariants(
            androidComponents.selector().withBuildType("release"),
        ) { variant ->
            (variant as HasUnitTestBuilder).enableUnitTest = false
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget = javaTarget
            allWarningsAsErrors.set(ci)
        }
    }

    plugins.withId(rootProject.libs.plugins.ktlint.get().pluginId) {
        val ktlint = extensions.getByName("ktlint") as KtlintExtension
        ktlint.filter {
            exclude("**/generated/**")
        }
        ktlint.version.set(rootProject.libs.versions.ktlint.binary)
    }

    plugins.withId(rootProject.libs.plugins.detekt.get().pluginId) {
        val detekt = extensions.getByType<DetektExtension>()

        detekt.baseline = file("detekt-baseline.xml")
        detekt.config.from(rootProject.file("gradle/detekt-config.yml"))
        detekt.buildUponDefaultConfig = true

        tasks.withType<Detekt>().configureEach {
            jvmTarget = javaVersion.toString()
            if (name == "detekt") {
                configureDefaultDetektTask(this)
            }
        }

        tasks.withType<DetektCreateBaselineTask>().configureEach {
            jvmTarget = javaVersion.toString()
            if (name == "detektBaseline") {
                configureDefaultDetektTask(this)
            }
        }

        tasks.named("check").configure {
            dependsOn("detekt")
        }
    }

    if (!project.name.contains(":sample:app")) {
        plugins.withId(rootProject.libs.plugins.maven.publish.get().pluginId) {
            configure<MavenPublishBaseExtension> {
                publishToMavenCentral(SonatypeHost("https://aws.oss.sonatype.org"))
            }
        }
    }
}

private fun configureDefaultDetektTask(task: SourceTask) {
    // The :detekt task in a multiplatform project doesn't do anything, it has no
    // sources configured. Instead, the Detekt plugin creates a Gradle task for each
    // source set, which then need to be wired manually to the 'release' task. This is
    // annoying and tedious.
    //
    // We make the default :detekt task analyze all .kt files, which is faster,
    // because only a single task runs, and we avoid all the wiring.
    task.setSource(task.project.layout.files("src"))
    task.exclude("**/api/**")
    task.exclude("**/build/**")
    task.exclude("**/detekt/**")
}
