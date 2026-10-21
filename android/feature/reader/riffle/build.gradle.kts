// The riffle scrubber (ADR-0022): the reader's fore-edge navigation strip, built against small interfaces so it can be
// tested and demonstrated on its own before the reader supplies real geometry, bookmarks and thumbnails.
plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
    alias(libs.plugins.roborazzi) // screenshot tests (ADR-0024), test classpath only
}

android {
    namespace = "com.flowfolio.feature.reader.riffle"
}

// Reference images live with the tests (ADR-0024): record and verify them on the development machine.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
