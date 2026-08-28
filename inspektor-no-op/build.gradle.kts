import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// A drop-in, empty replacement for the `:inspektor` module.
//
// It mirrors Inspektor's public API exactly but does nothing: the Ktor plugin installs no hooks,
// nothing is persisted, and no UI / database / Compose code is pulled in. This lets consumers
// depend on the real library only in their debug/dev variant and on this one in release/prod, so
// that neither the inspection code nor its transitive dependencies ever reach a production binary.
//
// Keep the public API here in sync with `:inspektor` - see the `api` directory of both modules.
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
        target.binaries {
            framework {
                baseName = "inspektor"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // The only dependency. Ktor is already on every consumer's classpath, so depending
                // on the no-op artifact adds no code and no transitive dependencies of its own.
                implementation(libs.ktor.core)
            }
        }

        // Test-only; none of this is published or reachable from the artifact.
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

android {
    // Deliberately different from `:inspektor` so the two can never collide in a merged manifest or
    // R class. This module contributes no manifest entries, resources or assets at all.
    namespace = "com.gyanoba.inspektor.noop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
