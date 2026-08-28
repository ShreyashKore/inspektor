import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    // Compile-time only, and only for `commonTest`: the content-negotiation tests declare an
    // `@Serializable` fixture. Nothing in `main` uses serialization any more.
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.mokkery)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// The Ktor client plugin: `install(Inspektor) { ... }`.
//
// Everything it does with the capture lives in `:inspektor-core`; this module owns only the
// pipeline hooks, the non-destructive body tee and the Ktor-typed configuration. `:inspektor-ui` is
// re-exported so that `install(Inspektor)` and `openInspektor()` keep arriving together, which is
// what the umbrella artifact promised before the split.
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

    val appleTargets = listOf(
        iosArm64, iosSimulatorArm64,
    )

    appleTargets.forEach { target ->
        with(target) {
            binaries {
                framework {
                    baseName = "InspektorKtor"
                    // SQLDelight's native driver binds to the system SQLite. Kotlin/Native does not add
                    // the library automatically, so every framework we link here needs it -- and so does a
                    // consumer's own framework, which is why the README asks for `-lsqlite3` in Xcode's
                    // Other Linker Flags.
                    linkerOpts("-lsqlite3")
                }
            }
        }
    }
    sourceSets {
        all {
            languageSettings.optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
        }
        val commonMain by getting {
            dependencies {
                api(project(":inspektor-core"))
                api(project(":inspektor-ui"))
                api(libs.ktor.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.androidx.annotation)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":inspektor-test-fixtures"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        val appleMain by creating {
            dependsOn(commonMain)
        }
        val appleTest by creating
        appleTest.dependsOn(commonTest)

        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
            getByName("${target.targetName}Test") { dependsOn(appleTest) }
        }
    }
}

android {
    namespace = "com.gyanoba.inspektor.ktor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

