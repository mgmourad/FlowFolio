// Macrobenchmarks and the baseline-profile generator (Phase 1 close-out). A test-only module: it drives the
// `benchmarkRelease` / `nonMinifiedRelease` builds of :app on a device and is never shipped.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.flowfolio.quality)
}

android {
    namespace = "com.flowfolio.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    // The app has two editions (Essential, Full); benchmarks drive the Essential one.
    flavorDimensions += "edition"
    productFlavors { create("essential") { dimension = "edition" } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

baselineProfile {
    // Run on the connected reference Fold, not a Gradle-managed emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
