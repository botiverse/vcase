pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
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
