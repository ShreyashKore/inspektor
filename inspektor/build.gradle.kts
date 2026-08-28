import org.gradle.kotlin.dsl.invoke
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.mokkery)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

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
                    baseName = "inspektor"
                }
            }
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
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                api(project(":inspektor-core"))
                implementation(libs.material.icons.core)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.json.io)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.core)
                implementation(libs.jsontree)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":inspektor-test-fixtures"))
                implementation(libs.kotlin.test)
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.uiTooling)
                implementation(libs.androidx.activityCompose)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.startup.runtime)
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
        val appleTest by creating
        appleTest.dependsOn(commonTest)

        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
            getByName("${target.targetName}Test") { dependsOn(appleTest) }
        }
    }
}

android {
    namespace = "com.gyanoba.inspektor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

