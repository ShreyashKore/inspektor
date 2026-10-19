import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

group = "com.gyanoba.inspektor.sample"
version = "1.0"

kotlin {
    // Declaring the `jvmCommonMain` dependsOn edge below would otherwise suppress the default
    // hierarchy, and with it `iosMain`.
    applyDefaultHierarchyTemplate()

    androidTarget()

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "InspektorSample"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatformSettings)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.mock)
            implementation(project(":inspektor"))
        }

        // Android and desktop share the OkHttp integration; iOS uses URLSession instead.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":inspektor-okhttp"))
            }
        }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.activityCompose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.startup.runtime)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }


        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(project(":inspektor-urlsession"))
        }

    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compilerOptions.options.freeCompilerArgs.add("-Xexport-kdoc")
    }

}

android {
    namespace = "com.gyanoba.inspektor.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }
    // Demonstrates shipping Inspektor in dev builds only. `dev` gets the real library, `prod` gets
    // the no-op artifact -- see the dependency substitution below.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

// The shared code calls `install(Inspektor)` and `openInspektor()` from `commonMain`, so the API has
// to be on the common compile classpath for every target -- KMP's metadata compilation cannot
// express a per-Android-flavor dependency. Instead, the `prod*` variants resolve `:inspektor` to
// `:inspektor-no-op`. This works precisely because the two expose an identical public API under the
// same package; if they ever diverge, the prod variants stop compiling.
//
// A pure-Android consumer needs none of this and can just write:
//   devImplementation("com.gyanoba.inspektor:inspektor:<version>")
//   prodImplementation("com.gyanoba.inspektor:inspektor-no-op:<version>")
configurations.matching { it.name.startsWith("prod") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(project(":inspektor"))
            .using(project(":inspektor-no-op"))
            .because("Inspektor must not ship in production builds")
        substitute(project(":inspektor-okhttp"))
            .using(project(":inspektor-okhttp-no-op"))
            .because("Inspektor must not ship in production builds")
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        buildTypes.release {
            proguard {
                configurationFiles.from("compose-desktop.pro")
            }
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.gyanoba.inspektor.sample"
            packageVersion = "1.0.0"
            modules("java.sql")
        }
    }
}