import kotlinx.validation.ExperimentalBCVApi

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
    alias(libs.plugins.detekt)
}

kotlin {
    explicitApi()

    androidTarget()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js {
        browser()
    }

    jvm()

    linuxArm64()
    linuxX64()

    macosArm64()
    macosX64()

    mingwX64()

    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    wasmJs {
        browser()
    }

    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()

    applyDefaultHierarchyTemplate()
}

android {
    namespace = "software.amazon.lastmile.kotlin.inject.anvil"
}

apiValidation {
    @OptIn(ExperimentalBCVApi::class)
    klib.enabled = true
}
