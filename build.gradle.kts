plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}

allprojects {
    group = "build.raft.vcase"
    version = providers.gradleProperty("vcase.version").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    // `publishToMavenLocal` lets a consumer try the artifacts before a real release
    // (see docs/pilot-raft-mobile.md).
    if (path != ":samples:compose-desktop") {
        apply(plugin = "maven-publish")
        // Release publishing (.github/workflows/release.yml): the botiverse Maven repository, the
        // same one botiverse/mermaid-native publishes to. Set only in that workflow.
        val mavenUrl = System.getenv("VCASE_MAVEN_URL")
        if (!mavenUrl.isNullOrBlank()) {
            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "botiverse"
                        url = uri(mavenUrl)
                        credentials {
                            username = System.getenv("VCASE_MAVEN_USER")
                            password = System.getenv("VCASE_MAVEN_TOKEN")
                        }
                    }
                }
            }
        }
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            events("failed")
        }
    }
}
