plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
    alias(libs.plugins.roborazzi) // component screenshots (ADR-0024, ADR-0052), test classpath only
}

android {
    namespace = "com.flowfolio.core.designsystem"
}

// Reference images live with the tests (ADR-0024): record and verify them on the development machine.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.haze) // backdrop blur for the mock's glass surfaces (ADR-0015, owner-approved 2026-10-17)
    api(libs.haze.blur) // Haze 2.0's blur effect lives in its own module

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
