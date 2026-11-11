// The PDF-to-EPUB converter (Phase 3, ADR-0009 as amended by ADR-0044): plain Kotlin/JVM, so the same pipeline runs in
// the app on the phone and on the desktop JVM for tests and the corpus quality gate. PDF access is behind
// [com.flowfolio.core.conversion.pdf.PdfSource]: :core:pdf implements it on Android, :tools:convert on the JVM.
plugins {
    alias(libs.plugins.flowfolio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // OCR's measured check against real scans runs ONNX Runtime on the JVM (ADR-0054); tests only.
    testImplementation(libs.onnxruntime.jvm)
}
