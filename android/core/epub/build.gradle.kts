// EPUB container, package document and navigation (ADR-0020). Built on Android's zip and XML pull parser only.
plugins {
    alias(libs.plugins.flowfolio.android.library)
}

android {
    namespace = "com.flowfolio.core.epub"
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
