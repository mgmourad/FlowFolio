// Phase 2 spike: compares EPUB pagination engines (WebView columns, native layout, hybrid) (Readium dropped: it needs desugar_jdk_libs, GPL-2.0+CPE).
// Throwaway: included only with -Pspikes. Results go to docs/spikes/2026-10-epub-pagination.md.
plugins {
    alias(libs.plugins.flowfolio.android.application)
    alias(libs.plugins.flowfolio.android.compose)
}

android {
    namespace = "com.flowfolio.spike.epub"

    defaultConfig {
        applicationId = "com.flowfolio.spike.epub"
        versionCode = 1
        versionName = "spike"
    }

    buildTypes {
        release {
            // Measured like a user build (no debuggable interpreter), but not minified, and installable.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.pagecurl)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
