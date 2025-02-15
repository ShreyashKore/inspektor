plugins {
    alias(libs.plugins.vanniktech).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.binaryCompatibility)
    alias(libs.plugins.kotlinx.kover)
}

allprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

apiValidation {
    ignoredProjects.addAll(listOf("sample", "data"))
}

buildscript {
    dependencies {
        classpath(libs.atomicfu)
    }
}
apply(plugin = "kotlinx-atomicfu")