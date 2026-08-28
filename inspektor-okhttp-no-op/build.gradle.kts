import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// A drop-in, empty replacement for `:inspektor-okhttp`.
//
// It mirrors that module's public API exactly and does nothing: `installInspektor` registers no
// interceptor, so not a byte of any request or response is read. Consumers keep the real
// integration on their debug/dev variant and this one on release/prod.
//
// Keep the public API here in sync with `:inspektor-okhttp` -- see the `api` directory of both.
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
        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Only the shared no-op declarations and OkHttp itself, which is already on the
                // consumer's classpath. No Inspektor code of any kind.
                api(project(":inspektor-core-no-op"))
                implementation(libs.okhttp)
            }
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlin.test)
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
    namespace = "com.gyanoba.inspektor.okhttp.noop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
