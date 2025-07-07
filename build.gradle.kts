plugins {
    alias(libs.plugins.vanniktech).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.atomifu)
    alias(libs.plugins.binaryCompatibility)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.mokkery).apply(false)
}

allprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

apiValidation {
    ignoredProjects.addAll(listOf("sample"))
    ignoredPackages.addAll(listOf("com.gyanoba.inspektor.data"))
    ignoredClasses.add("**.ComposableSingletons$*Kt")
    ignoredClasses.add("**.MainActivityKt") // If MainActivityKt also has unstable parts
    ignoredClasses.add("**.AppKt")
}

buildscript {
    dependencies {
        classpath(libs.atomicfu)
    }
}