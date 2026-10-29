plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.pdfium)
}

android {
    namespace = "com.flowfolio.core.pdf"
}

dependencies {
    api(projects.core.model)
    // The converter's view of a PDF (Phase 3): this module implements its PdfSource on the phone.
    api(projects.core.conversion)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
