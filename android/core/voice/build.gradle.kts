plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.onnxruntime)
}

android {
    namespace = "com.flowfolio.core.voice"
    // Same ABIs as PDFium (arm64-v8a, armeabi-v7a, x86_64; no x86), so every build that has PDFium has the OCR runtime.
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
