import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech)
}

group = "com.gyanoba.inspektor"
version = project.properties["VERSION_NAME"]!!

// `NSURLSession` capture for iOS. One integration covers Alamofire, Moya, Get and raw URLSession,
// because all of them are URLSession underneath.
//
// Its own artifact rather than `:inspektor-core`'s `appleMain`, so that non-iOS consumers and the
// umbrella do not carry it.
kotlin {
    explicitApiWarning()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    val appleTargets = listOf(iosArm64, iosSimulatorArm64)

    // What an app that is not Kotlin links -- a Swift or React Native one, through
    // InspektorUrlSession.podspec: `./gradlew :inspektor-urlsession:assembleInspektorUrlSessionReleaseXCFramework`.
    // It exports core too, or `InspektorRemote` and `LogLevel` would be missing from its headers.
    // Static, so CocoaPods has nothing to embed; the app links SQLite itself (the podspec says so).
    val xcframework = XCFramework("InspektorUrlSession")
    appleTargets.forEach { target ->
        target.binaries.framework {
            baseName = "InspektorUrlSession"
            export(project(":inspektor-core"))
            isStatic = true
            xcframework.add(this)
        }
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
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":inspektor-test-fixtures"))
            }
        }
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":inspektor-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val appleTest by creating { dependsOn(commonTest) }

        appleTargets.forEach { target ->
            getByName("${target.targetName}Main") { dependsOn(appleMain) }
            getByName("${target.targetName}Test") { dependsOn(appleTest) }
        }
    }
}
