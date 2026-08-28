import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// The declarations every no-op twin needs: `LogLevel`, `UnstableInspektorAPI` and
// `setApplicationId`.
//
// Factored out so that `inspektor-no-op` and `inspektor-okhttp-no-op` can be used together without
// two copies of `com.gyanoba.inspektor.LogLevel` colliding on the classpath -- and so that an
// OkHttp-only consumer does not drag in the Ktor no-op just to get the shared types.
//
// Zero dependencies. Nothing here does anything.
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

    sourceSets {
        val commonMain by getting
        val appleMain by creating { dependsOn(commonMain) }
        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
        }
    }
}

android {
    namespace = "com.gyanoba.inspektor.core.noop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
