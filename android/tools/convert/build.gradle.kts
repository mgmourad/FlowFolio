// The converter on the desktop JVM, for development only (owner, 2026-10-27: conversion runs on the phone in the
// product). It reads PDFs through PDFium's own Windows and Linux builds via JNA, runs the same :core:conversion pipeline
// as the app, and holds the corpus harness: metrics, baselines and the CI quality gate. Never shipped.
plugins {
    alias(libs.plugins.flowfolio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(projects.core.conversion)
    implementation(libs.jna)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.epubcheck)
}

application {
    mainClass.set("com.flowfolio.tools.convert.MainKt")
}

/** PDFium for this machine, from the release the app uses, checked by SHA-256 (FetchHostPdfiumTask). */
val pdfiumDir = layout.buildDirectory.dir("pdfium")
val fetchPdfium = tasks.register<FetchHostPdfiumTask>("fetchPdfium") {
    release.set(Pdfium.RELEASE)
    asset.set(FetchHostPdfiumTask.hostAsset())
    outputDir.set(pdfiumDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(fetchPdfium)
    systemProperty("jna.library.path", pdfiumDir.get().asFile.absolutePath)
    // The corpus lives outside the repo (samples/corpus/cache); tests that need it skip when it is absent.
    // -Pflowfolio.corpus=<dir> checks another folder, e.g. only the CC BY books that CI checks.
    systemProperty(
        "flowfolio.corpus",
        providers.gradleProperty("flowfolio.corpus").getOrElse(rootProject.file("../samples/corpus/cache").absolutePath)
    )
    systemProperty("flowfolio.golden", rootProject.file("../samples/golden").absolutePath)
    // -Pflowfolio.recordBaselines writes the corpus metrics as the new baselines instead of checking against them.
    systemProperty(
        "flowfolio.recordBaselines",
        providers.gradleProperty("flowfolio.recordBaselines").isPresent.toString()
    )
    // A corpus run converts every book; Gradle can't see the PDFs, so never take the result from the cache.
    outputs.upToDateWhen { false }
    maxHeapSize = "2g"
}

tasks.named<JavaExec>("run") {
    dependsOn(fetchPdfium)
    systemProperty("jna.library.path", pdfiumDir.get().asFile.absolutePath)
    maxHeapSize = "2g"
}
