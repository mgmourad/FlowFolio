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
    api(libs.haze) // backdrop blur for the mock's glass surfaces (ADR-0015, owner-approved 2026-10-17)
    api(libs.haze.blur) // Haze 2.0's blur effect lives in its own module
}
