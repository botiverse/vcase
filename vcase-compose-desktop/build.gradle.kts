// Compose Desktop adapter: scans @VCase functions on the test classpath
// (ComposablePreviewScanner's JvmAnnotationScanner), renders every axis variant offscreen
// with skiko (ImageComposeScene: no window, no GPU) through the project's VCaseHost, and
// records or verifies the goldens with vcase-core. Renderer id: desktop-skiko.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":vcase-core"))
    // The consumer brings the native skiko runtime for its OS (compose.desktop.currentOs).
    api(libs.cmp.runtime)
    api(libs.cmp.ui)
    implementation(libs.cmp.foundation)
    api(libs.junit)
    implementation(libs.cps.jvm)
    implementation(libs.classgraph)

    testImplementation(compose.desktop.currentOs)
    testImplementation(kotlin("test-junit"))
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
