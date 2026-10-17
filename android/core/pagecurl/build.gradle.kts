plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
}

android {
    namespace = "com.flowfolio.core.pagecurl"
}

dependencies {
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // foundation (gestures, layout) comes with material3's dependency set via the Compose BOM
    implementation(libs.androidx.compose.material3)
}

// Vendored third-party code: keep it close to upstream rather than reformatting it to our style.
tasks.matching { it.name.startsWith("spotless") || it.name == "detekt" }.configureEach { enabled = false }
