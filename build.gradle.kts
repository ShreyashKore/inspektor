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
    ignoredProjects.addAll(listOf("sample", "inspektor-test-fixtures"))

    // The Compose compiler emits a public `ComposableSingletons$<File>Kt` holder per file that
    // contains composable lambdas. They are a compiler implementation detail -- their names encode
    // the owning Gradle module, so they churn whenever code moves -- and nothing in `ui` is public
    // API on purpose, so the whole package is excluded. `ignoredClasses` takes fully qualified
    // names only (the `**.ComposableSingletons$*Kt` glob that used to be here never matched
    // anything), hence the two entries below are spelled out.
    ignoredPackages.add("com.gyanoba.inspektor.ui")
    ignoredClasses.add("com.gyanoba.inspektor.ComposableSingletons\u0024Inspektor_jvmKt")
    ignoredClasses.add("com.gyanoba.inspektor.ComposableSingletons\u0024Inspektor_androidKt")
    ignoredClasses.add("com.gyanoba.inspektor.ComposableSingletons\u0024MainActivityKt")
}

buildscript {
    dependencies {
        classpath(libs.atomicfu)
    }
}