plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    maxHeapSize = "2g"
}

dependencies {
    implementation(projects.runtime)
    implementation(libs.ksp.api)

    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)

    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    // Gives us access to annotations.
    implementation(libs.kotlin.inject.runtime)

    testImplementation(libs.assertk)
    testImplementation(libs.kotlin.compile.testing.core)
    testImplementation(libs.kotlin.compile.testing.ksp)

    testImplementation(platform(libs.junit.jupiter.bom))
    testImplementation(libs.junit.jupiter.core)
    testRuntimeOnly(libs.junit.jupiter.launcher)

    // Added so that the SymbolProcessor is picked up in tests.
    testImplementation(libs.kotlin.inject.ksp)

    // Bump transitive dependency.
    testImplementation(libs.ksp)
}
