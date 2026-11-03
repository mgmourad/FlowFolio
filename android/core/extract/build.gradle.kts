plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.hilt)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.flowfolio.core.extract"

    defaultConfig {
        // Chaquopy needs the ABIs named. Python 3.12 ships only 64-bit, which every minSdk 29 phone we target is;
        // on anything else the extractor simply does not start and the generic resolver carries on (ADR-0047).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            // yt-dlp is Unlicense (public domain) and pure Python; its GPL'd release *binaries* are not used, and
            // nothing here needs ffmpeg, since we only ask it for an address to play (ADR-0047).
            install("yt-dlp==${libs.versions.ytdlp.get()}")
        }
    }
}

dependencies {
    api(projects.core.data)

    testImplementation(libs.junit4)
}
