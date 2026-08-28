import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinx.serialization)
}

// Test-only helpers shared by every module's tests: an in-memory database, an in-memory KStore
// codec and a no-op data source.
//
// Deliberately NOT published -- there is no `vanniktech` plugin here and no `gradle.properties`
// with POM coordinates, and it is in `apiValidation.ignoredProjects`. It exists so that
// `:inspektor`, `:inspektor-okhttp` and friends stop each carrying their own copy of the same
// column-adapter wiring, which is exactly how the two copies drifted apart before.
//
// The helpers live in `main` rather than `test` source sets because Kotlin Multiplatform cannot
// export a test source set to another module. Nothing consumes this module outside of tests.
kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    val appleTargets = listOf(iosArm64, iosSimulatorArm64)

    sourceSets {
        all {
            languageSettings.optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
        }
        val commonMain by getting {
            dependencies {
                api(project(":inspektor-core"))
                api(libs.kotlin.test)
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.io.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(libs.kstore)
            }
        }
        val androidMain by getting {
            dependencies {
                // `kotlin-test`'s JVM variant only provides the `@BeforeTest`/`@AfterTest`
                // typealiases through a test framework; main compilations do not pick one on
                // their own, so name it explicitly.
                api(libs.kotlin.test.junit)
                // Android unit tests run on the host JVM, where `android.database` is stubbed out,
                // so the in-memory driver has to be the JDBC one. This module never ships.
                implementation(libs.sqlDelight.driver.sqlite)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.kotlin.test.junit)
                implementation(libs.sqlDelight.driver.sqlite)
            }
        }
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.sqlDelight.driver.native)
            }
        }
        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
        }
    }
}

android {
    namespace = "com.gyanoba.inspektor.testfixtures"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
