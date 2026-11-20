// Platform-neutral engine: manifest model and JSON, axis expansion and naming, id rules,
// manifest diff, exact image comparison, golden store and the static HTML report.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":vcase-annotations"))
    api(libs.kotlinx.serialization.json)
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
