plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.tesseract)
}

android {
    namespace = "com.flowfolio.core.ocr"
}

dependencies {
    // The converter's view of OCR (OcrReader, OcrImage, OcrPage); this module is Tesseract's implementation of it.
    api(projects.core.conversion)

    testImplementation(libs.junit4)
}
