// Dependency-free annotations, usable from commonMain of any Kotlin Multiplatform module.
// Android consumers resolve the jvm variant (Kotlin's platform compatibility rule lets an
// androidJvm consumer use a jvm library), so there is no separate Android target and no AGP.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    // Web targets so a Kotlin/JS or Wasm UI can share case ids too; tests run on Node only.
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
