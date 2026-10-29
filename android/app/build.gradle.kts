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
        // ADR-0042: the release version lives in gradle.properties, so a release changes one file.
        versionCode = providers.gradleProperty("flowfolio.versionCode").get().toInt()
        versionName = providers.gradleProperty("flowfolio.versionName").get()
        // The adb hooks the measurement tools use (openBookId, importPath, startPage, gcNow): debug builds always
        // have them; of the release-like builds only the baseline-profile plugin's benchmark build types do.
        buildConfigField("boolean", "MEASUREMENT_HOOKS", "false")
    }

    buildFeatures { buildConfig = true }

    // ADR-0036: one fixed key for the dev builds the owner sideloads, so each APK updates the last in place. It lives
    // outside the repo (`flowfolio.devSigning.*` in ~/.gradle/gradle.properties); without it (CI, other machines)
    // debug builds keep the SDK's own debug key.
    val devStore = providers.gradleProperty("flowfolio.devSigning.storeFile").orNull
    val devSigning = devStore?.let {
        signingConfigs.create("dev") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("flowfolio.devSigning.storePassword").get()
            keyAlias = providers.gradleProperty("flowfolio.devSigning.keyAlias").get()
            keyPassword = providers.gradleProperty("flowfolio.devSigning.keyPassword").get()
        }
    }

    // ADR-0042: the release key, also outside the repo (`flowfolio.releaseSigning.*`). Without it release builds are
    // unsigned and can't be published.
    val releaseStore = providers.gradleProperty("flowfolio.releaseSigning.storeFile").orNull
    val releaseSigning = releaseStore?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("flowfolio.releaseSigning.storePassword").get()
            keyAlias = providers.gradleProperty("flowfolio.releaseSigning.keyAlias").get()
            keyPassword = providers.gradleProperty("flowfolio.releaseSigning.keyPassword").get()
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a future release build on the same device.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            devSigning?.let { signingConfig = it }
        }
        release {
            releaseSigning?.let { signingConfig = it }
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
