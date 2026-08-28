import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.atomifu)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.mokkery)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// Capture-agnostic heart of Inspektor: the SQLDelight store, the transaction recorder every
// integration drives, the override engine, HAR export and the platform abstractions.
//
// It knows nothing about Ktor, OkHttp, URLSession or Compose -- deliberately. Anything added here
// is paid for by every consumer of every integration, so new dependencies need a real reason.
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
        target.binaries.framework { baseName = "InspektorCore" }
    }


    // SQLDelight's native driver binds to the system SQLite and Kotlin/Native does not add the
    // library on its own, so every native binary -- frameworks and test executables alike --
    // needs it. Consumers need the same flag in Xcode's Other Linker Flags.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach { linkerOpts("-lsqlite3") }
    }

    sourceSets {
        all {
            languageSettings.optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.sqlDelight.coroutines.extensions)
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kstore)
                implementation(libs.kstore.file)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqlDelight.driver.android)
                implementation(libs.androidx.startup.runtime)
                implementation(libs.androidx.core.ktx)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                // Android unit tests run on the host JVM, so they need the JDBC driver for the
                // in-memory test database. Test-only: it never reaches the published AAR.
                implementation(libs.sqlDelight.driver.sqlite)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqlDelight.driver.sqlite)
            }
        }
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.stately.common)
                implementation(libs.stately.iso.collections)
                implementation(libs.sqlDelight.driver.native)
            }
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
    namespace = "com.gyanoba.inspektor.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("InspektorDatabase") {
            packageName.set("com.gyanoba.inspektor.data")
        }
    }
}
