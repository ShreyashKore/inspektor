import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// The transaction viewer: every Compose screen, `openInspektor()` and the platform bits that only
// a UI needs (sharing a HAR file, the activity, the file provider).
//
// Separate from `:inspektor-core` so that a headless consumer -- an OkHttp interceptor feeding a
// desktop viewer, say -- does not pay for Compose Multiplatform. This module is 59% of the source.
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
    val appleTargets = listOf(iosArm64, iosSimulatorArm64)

    appleTargets.forEach { target ->
        target.binaries.framework {
            baseName = "InspektorUi"
            // SQLDelight's native driver binds to the system SQLite. Kotlin/Native does not add
            // the library automatically, so every framework we link here needs it -- and so does a
            // consumer's own framework, which is why the README asks for `-lsqlite3` in Xcode's
            // Other Linker Flags.
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
            }
        }
        val commonMain by getting {
            dependencies {
                api(project(":inspektor-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.material.icons.core)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.json.io)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.jsontree)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.uiTooling)
                implementation(libs.androidx.activityCompose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.stately.common)
                implementation(libs.stately.iso.collections)
            }
        }
        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
        }
    }
}

compose.resources {
    // Pinned rather than derived from the module name, so the generated `Res` class keeps a stable
    // package if this module is ever renamed again.
    packageOfResClass = "com.gyanoba.inspektor.ui.generated.resources"
}

android {
    namespace = "com.gyanoba.inspektor.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
