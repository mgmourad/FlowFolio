plugins {
    alias(libs.plugins.flowfolio.android.application)
    alias(libs.plugins.flowfolio.android.compose)
    alias(libs.plugins.flowfolio.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

val benchmarkBuildTypes = setOf("benchmarkRelease", "nonMinifiedRelease")

android {
    namespace = "com.flowfolio"

    defaultConfig {
        applicationId = "com.flowfolio"
        versionCode = 1
        versionName = "0.1.0"
        // The adb hooks the measurement tools use (openBookId, importPath, startPage, gcNow): debug builds always
        // have them; of the release-like builds only the baseline-profile plugin's benchmark build types do.
        buildConfigField("boolean", "MEASUREMENT_HOOKS", "false")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            // Lets a debug build sit next to a future release build on the same device.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Created by the baseline-profile plugin from `release`: installable (debug key) and drivable from adb, so
        // :benchmark can open a book. Never shipped.
        configureEach {
            if (name in benchmarkBuildTypes) signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// The plugin copies `release` into these build types after the DSL runs, so the flag is set per variant instead.
androidComponents {
    onVariants { variant ->
        if (variant.buildType in benchmarkBuildTypes) {
            variant.buildConfigFields?.put(
                "MEASUREMENT_HOOKS",
                com.android.build.api.variant.BuildConfigField("boolean", "true", "adb hooks for :benchmark")
            )
        }
    }
}

baselineProfile {
    // Profiles are recorded on a device by hand (see CLAUDE.md), then committed; builds never record one.
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(projects.benchmark)
    implementation(libs.androidx.profileinstaller)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.feature.library)
    implementation(projects.feature.reader)
    implementation(projects.feature.reader.riffle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
