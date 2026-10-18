import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// A drop-in, empty replacement for `:inspektor-ui`: an `openInspektor()` that does nothing.
//
// `openInspektor()` is the one name in the viewer that app code calls, so an app that pairs
// `inspektor-ui` with `inspektor-okhttp` or `inspektor-urlsession` needs this twin in release, or
// that call stops compiling. `inspektor-no-op` re-exports it, the same way `inspektor` re-exports
// `inspektor-ui`.
//
// Zero dependencies, and no Activity, FileProvider, resources or Compose: nothing here does
// anything. Keep the public API in sync with `:inspektor-ui` -- see the `api` directory of both.
kotlin {
    explicitApiWarning()
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    listOf(iosArm64, iosSimulatorArm64).forEach { target ->
        target.binaries.framework { baseName = "InspektorUi" }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    // Deliberately different from `:inspektor-ui` so the two can never collide in a merged manifest
    // or R class.
    namespace = "com.gyanoba.inspektor.ui.noop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
