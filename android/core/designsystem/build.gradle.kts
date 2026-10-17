plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
}

android {
    namespace = "com.flowfolio.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
}
