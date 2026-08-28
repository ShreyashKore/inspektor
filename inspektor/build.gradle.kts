import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// The umbrella artifact. It keeps the coordinates and the meaning it always had --
// `com.gyanoba.inspektor:inspektor` is "the Ktor plugin plus the viewer" -- and now carries no
// source of its own; every declaration reaches consumers transitively from the three modules
// below.
//
// `api(...)` rather than `implementation(...)` is what makes that work: the split must not change
// anything on an existing consumer's compile classpath. `:inspektor-ktor` already re-exports
// `:inspektor-core` and `:inspektor-ui`, but they are named explicitly here so that the umbrella's
// contract does not depend on that staying true.
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
            baseName = "inspektor"
            // A source-free Kotlin/Native module produces an *empty* framework unless its
            // dependencies are exported, so a Swift consumer would see no Inspektor symbols at
            // all. Gradle/KMP consumers are unaffected either way.
            export(project(":inspektor-core"))
            export(project(":inspektor-ui"))
            export(project(":inspektor-ktor"))
            // SQLDelight's native driver binds to the system SQLite. Kotlin/Native does not add
            // the library automatically, so every framework we link here needs it -- and so does a
            // consumer's own framework, which is why the README asks for `-lsqlite3` in Xcode's
            // Other Linker Flags.
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":inspektor-core"))
                api(project(":inspektor-ui"))
                api(project(":inspektor-ktor"))
            }
        }
    }
}

android {
    namespace = "com.gyanoba.inspektor.umbrella"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
