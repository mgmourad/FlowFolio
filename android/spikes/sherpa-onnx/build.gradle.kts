// Spike: confirm sherpa-onnx (Kokoro TTS) integrates and synthesizes on-device from filesDir (download-on-first-use path).
// Throwaway: included only with -Pspikes, never in CI or the shipped app. Results go to docs/spikes/.
plugins {
    alias(libs.plugins.flowfolio.android.application)
}

android {
    namespace = "com.flowfolio.spike.tts"

    defaultConfig {
        applicationId = "com.flowfolio.spike.tts"
        versionCode = 1
        versionName = "spike"
        // The Fold is arm64; keep the spike APK small by packaging only that ABI.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
}

dependencies {
    // Prebuilt AAR from https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1 (all ABIs; classes + jni).
    // Downloaded by hand into libs/ (gitignored): the spike is throwaway, so it is not fetched at build time.
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation(libs.androidx.core.ktx)
}
