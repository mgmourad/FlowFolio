// The riffle scrubber (ADR-0022): the reader's fore-edge navigation strip, built against small interfaces so it can be
// tested and demonstrated on its own before the reader supplies real geometry, bookmarks and thumbnails.
plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
}

android {
    namespace = "com.flowfolio.feature.reader.riffle"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
}
