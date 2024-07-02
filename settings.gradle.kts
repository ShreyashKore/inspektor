rootProject.name = "inspektor"
include(":inspektor")
include(":sample")
//includeBuild("convention-plugins")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
//        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
        google()
        mavenCentral()
    }
}
