// A minimal consumer: a few components, their @VCase functions, a VCaseHost and the one test
// class. Goldens: goldens/desktop-skiko/, manifest: vcase.manifest.json.
//
//   ./gradlew :samples:compose-desktop:test                      verify (zero tolerance)
//   ./gradlew :samples:compose-desktop:test -Pvcase.mode=record  record new / changed goldens
//   ./gradlew :samples:compose-desktop:test -Pvcase.mode=scan    rewrite the manifest only
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":vcase-annotations"))
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.ui)

    testImplementation(project(":vcase-compose-desktop"))
    testImplementation(compose.desktop.currentOs)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    val goldens = layout.projectDirectory.dir("goldens")
    inputs.files(fileTree(goldens), layout.projectDirectory.file("vcase.manifest.json")).withPropertyName("vcaseInputs")
    providers.gradleProperty("vcase.mode").orNull?.let {
        systemProperty("vcase.mode", it)
        outputs.upToDateWhen { false }
    }
    providers.gradleProperty("vcase.filter").orNull?.let { systemProperty("vcase.filter", it) }
    systemProperty("vcase.goldens", goldens.asFile.absolutePath)
    systemProperty("vcase.manifest", layout.projectDirectory.file("vcase.manifest.json").asFile.absolutePath)
    systemProperty("vcase.out", layout.buildDirectory.dir("vcase").get().asFile.absolutePath)
    systemProperty("vcase.sourceRoot", "samples/compose-desktop/src/main/kotlin")
    systemProperty("java.awt.headless", "true")
}
