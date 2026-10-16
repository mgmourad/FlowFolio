import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

// Phase 1 spike: compares PDF renderers, probes PDFium extraction, and measures page curl.
// Throwaway: included only with -Pspikes. Results go to docs/spikes/.
plugins {
    alias(libs.plugins.flowfolio.android.application)
    alias(libs.plugins.flowfolio.android.compose)
}

/** Downloads prebuilt PDFium (bblanchon/pdfium-binaries), verifies SHA-256, unpacks per ABI. */
abstract class FetchPdfium : DefaultTask() {
    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val fs: FileSystemOperations

    @get:Input abstract val release: Property<String>

    /** ABI -> "assetName|sha256" */
    @get:Input abstract val assets: MapProperty<String, String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        assets.get().forEach { (abi, spec) ->
            val (asset, sha) = spec.split('|')
            val tgz = File(temporaryDir, asset)
            val url = "https://github.com/bblanchon/pdfium-binaries/releases/download/${release.get()}/$asset"
            URI(url).toURL().openStream().use { input -> tgz.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(tgz.readBytes()).joinToString("") {
                "%02x".format(it)
            }
            check(actual == sha) { "SHA-256 mismatch for $asset: $actual" }
            fs.sync {
                from(archives.tarTree(archives.gzip(tgz)))
                into(File(out, abi))
            }
            fs.copy {
                from(File(out, "$abi/lib/libpdfium.so"))
                into(File(out, "jniLibs/$abi"))
            }
        }
    }
}

val pdfiumDir = layout.buildDirectory.dir("pdfium")
val fetchPdfium by tasks.registering(FetchPdfium::class) {
    release.set("chromium/8100")
    assets.put("arm64-v8a", "pdfium-android-arm64.tgz|e9bab2929443d3d881166af9ec1d191c76ecdd37effb07a58743a7d7b507e149")
    assets.put("x86_64", "pdfium-android-x64.tgz|b5cd96bc81c3414a84ce1f3eb928d6fe6981b13fe555f21bfa931bc478f51876")
    outputDir.set(pdfiumDir)
}

android {
    namespace = "com.flowfolio.spike.pdf"
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.flowfolio.spike.pdf"
        versionCode = 1
        versionName = "spike"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += "-DPDFIUM_ROOT=${pdfiumDir.get().asFile.invariantSeparatorsPath}"
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    sourceSets.getByName("main").jniLibs.directories.add(pdfiumDir.get().dir("jniLibs").asFile.path)

    buildTypes {
        // Measurements use release (debug Compose is several times slower). Debug-signed, not minified.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchPdfium) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.spike.pagecurl)
    implementation(libs.spike.androidx.pdf.document.service)
    implementation(libs.spike.onnxruntime.android)
}
