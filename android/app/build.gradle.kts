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
        // ADR-0057: the processor types FlowFolio ships for. 32-bit x86 (53 MB that some prebuilt libraries bring) is
        // left out: no phone uses it, and x86_64 covers emulators.
        ndk { abiFilters += SHIPPED_ABIS }
    }

    // ADR-0057: a release is also built as one APK per processor type, beside the universal APK, so a phone downloads
    // only its own code (about 150 MB instead of 357 MB on arm64). Only for release builds: the debug APK handed over
    // for testing stays one file.
    val releasing = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    splits {
        abi {
            isEnable = releasing
            reset()
            include(*SHIPPED_ABIS.toTypedArray())
            isUniversalApk = true
        }
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

    // ADR-0042: the release key, also outside the repo (`flowfolio.releaseSigning.*`). Without it a release build
    // would come out unsigned, which `checkReleaseSigning` below turns into a failure rather than a surprise.
    val releaseStore = providers.gradleProperty("flowfolio.releaseSigning.storeFile").orNull
    val releaseSigning = releaseStore?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("flowfolio.releaseSigning.storePassword").get()
            keyAlias = providers.gradleProperty("flowfolio.releaseSigning.keyAlias").get()
            keyPassword = providers.gradleProperty("flowfolio.releaseSigning.keyPassword").get()
            // v1 (JAR signing) is only needed below API 24 and minSdk is 29, so it is dead weight.
            // v3 is the one that matters here: it is the only scheme that can carry a rotation lineage,
            // so an APK without it can never nominate a successor key even while we still hold this one.
            // The first release key was lost with no way back (ADR-0049); this is what keeps that door open.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
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

/**
 * A release must be signed with the release key, and nothing else (ADR-0042).
 *
 * Without `flowfolio.releaseSigning.*` the release build type simply has no signing config, and `assembleRelease`
 * hands back an unsigned APK with no complaint -- which is how 0.3.0-alpha.2 came to be signed by hand with the dev
 * key. The dev key signs every sideloaded build and lives on a development machine; it must never become the
 * identity users update against. So the build stops instead, and says what is missing.
 */
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    val keyed = providers.gradleProperty("flowfolio.releaseSigning.storeFile").orNull != null
    doLast {
        check(keyed) {
            "No release signing key. Set flowfolio.releaseSigning.storeFile, .storePassword, .keyAlias and " +
                ".keyPassword in ~/.gradle/gradle.properties, pointing at the keystore for " +
                "CN=FlowFolio Release (ADR-0042). Never sign a release with the dev key: Android treats a " +
                "differently signed APK as a different app, so no existing install can upgrade."
        }
    }
}

tasks.matching { it.name == "packageRelease" }.configureEach { dependsOn(checkReleaseSigning) }

/**
 * Copies the repository's `CHANGELOG.md` into the APK's assets (ADR-0052), so What's new reads this build's own notes
 * on the phone, offline, instead of asking the releases page.
 */
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        changelog.get().asFile.copyTo(out.resolve("CHANGELOG.md"), overwrite = true)
    }
}

val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") {
    changelog.set(rootProject.layout.projectDirectory.file("../CHANGELOG.md"))
}

// The plugin copies `release` into these build types after the DSL runs, so the flag is set per variant instead.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::outputDir)
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
    implementation(projects.core.extract)
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

/** The processor types a release ships for (ADR-0057). */
val SHIPPED_ABIS: List<String> get() = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
