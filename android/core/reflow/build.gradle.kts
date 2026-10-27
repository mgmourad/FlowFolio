// The reflow engine (ADR-0019): EPUB chapters as blocks, paginated with Android's text layout and drawn to page images.
plugins {
    alias(libs.plugins.flowfolio.android.library)
}

android {
    namespace = "com.flowfolio.core.reflow"
}

dependencies {
    api(projects.core.epub)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // The reading fonts (M3's check: pagination is exact in every font).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(projects.core.designsystem)
}
