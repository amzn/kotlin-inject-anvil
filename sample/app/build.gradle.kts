plugins {
    alias(libs.plugins.android.app)
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

android {
    namespace = "software.amazon.lastmile.kotlin.inject.anvil.sample.app"
}

dependencies {
    kspCommonMainMetadata(libs.kotlin.inject.ksp.bugfix)
    add("kspAndroid", libs.kotlin.inject.ksp.bugfix)
    add("kspIosSimulatorArm64", libs.kotlin.inject.ksp.bugfix)

    kspCommonMainMetadata(projects.compiler)
    add("kspAndroid", projects.compiler)
    add("kspIosSimulatorArm64", projects.compiler)

    commonMainImplementation(projects.runtime)
    commonMainImplementation(libs.kotlin.inject.runtime)

    commonMainImplementation(projects.sample.lib)

    commonTestImplementation(libs.assertk)
    commonTestImplementation(libs.kotlin.test)
}
