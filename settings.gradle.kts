pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "inspektor"
include(":inspektor")
include(":inspektor-core")
include(":inspektor-ktor")
include(":inspektor-okhttp")
include(":inspektor-ui")
include(":inspektor-urlsession")
include(":inspektor-test-fixtures")
include(":inspektor-core-no-op")
include(":inspektor-no-op")
include(":inspektor-okhttp-no-op")
include(":inspektor-urlsession-no-op")
include(":sample")
