plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    androidTarget()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}

android {
    namespace = "software.amazon.lastmile.kotlin.inject.anvil.sample.lib"
}

dependencies {
    kspCommonMainMetadata(libs.kotlin.inject.ksp)
    add("kspAndroid", libs.kotlin.inject.ksp)
    add("kspIosSimulatorArm64", libs.kotlin.inject.ksp)

    kspCommonMainMetadata(projects.compiler)
    add("kspAndroid", projects.compiler)
    add("kspIosSimulatorArm64", projects.compiler)

    commonMainImplementation(projects.runtime)
    commonMainImplementation(libs.kotlin.inject.runtime)
}
