plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// A drop-in, empty replacement for `:inspektor-urlsession`.
//
// It mirrors that module's public API and does nothing: no delegate method is implemented, no
// URLProtocol is registered, and nothing is read or persisted.
//
// Keep the public API here in sync with `:inspektor-urlsession`. Neither module can be checked by
// binary-compatibility-validator, which reads JVM and Android class files and finds neither here,
// so the two are compared by eye -- and by the `:sample` iOS build, which compiles against both.
kotlin {
    explicitApiWarning()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    val appleTargets = listOf(iosArm64, iosSimulatorArm64)

    appleTargets.forEach { target ->
        target.binaries.framework { baseName = "InspektorUrlSession" }
    }

    sourceSets {
        all {
            languageSettings.optIn("com.gyanoba.inspektor.UnstableInspektorAPI")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Only the shared no-op declarations. No Inspektor code of any kind.
                api(project(":inspektor-core-no-op"))
            }
        }
        val appleTest by creating { dependsOn(commonTest) }
        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
            getByName("${target.targetName}Test") { dependsOn(appleTest) }
        }
    }
}
