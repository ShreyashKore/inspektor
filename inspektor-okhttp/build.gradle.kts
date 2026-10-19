import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// An OkHttp `Interceptor` that records into the same store, UI and override engine as the Ktor
// plugin. Android and JVM only -- OkHttp exists nowhere else.
//
// A Kotlin Multiplatform module rather than a plain Java library, so it shares one source set
// across both targets and matches the rest of the build.
kotlin {
    explicitApiWarning()
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
        }
        val commonMain by getting
        val commonTest by getting

        // Both targets are JVM, so all the source is shared here rather than in `commonMain` --
        // that keeps OkHttp out of the metadata compilation, which has no JVM classpath.
        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":inspektor-core"))
                api(libs.okhttp)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(project(":inspektor-test-fixtures"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.okhttp.mockwebserver)
            }
        }

        val jvmMain by getting { dependsOn(jvmCommonMain) }
        val jvmTest by getting { dependsOn(jvmCommonTest) }
        val androidMain by getting { dependsOn(jvmCommonMain) }
        val androidUnitTest by getting { dependsOn(jvmCommonTest) }
    }
}

android {
    namespace = "com.gyanoba.inspektor.okhttp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
