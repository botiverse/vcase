pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    // Kotlin/JS and Wasm add the Node.js / Yarn distribution repositories at project level.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "vcase"

include(
    ":vcase-annotations",
    ":vcase-core",
    ":vcase-compose-desktop",
    ":samples:compose-desktop",
)
