plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.hilt)
}

android {
    namespace = "com.flowfolio.core.data"
}

// Reuse :core:pdf's generated robustness PDFs as fixtures for on-device import tests.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("androidTest").assets.directories.add("../pdf/src/androidTest/assets")
}

dependencies {
    api(projects.core.model)
    api(projects.core.database)
    api(projects.core.epub)
    implementation(projects.core.datastore)
    api(projects.core.pdf)
    implementation(libs.kotlinx.coroutines.android)
    // The Dynamic Flow bundle's flowfolio.json (ADR-0045); its serializers come with :core:conversion.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    // OCR of scanned pages (ADR-0054) runs on Microsoft's ONNX Runtime (ADR-0059), through :core:voice's own bridge,
    // and on Tesseract built from source (Arabic), through :core:ocr.
    implementation(projects.core.voice)
    implementation(projects.core.ocr)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
