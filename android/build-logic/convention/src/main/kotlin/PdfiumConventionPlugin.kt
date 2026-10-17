import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Prebuilt PDFium (bblanchon/pdfium-binaries) for the JNI bridge (ADR-0010).
 * Downloaded at build time, verified by SHA-256, never committed. Update release + hashes together.
 */
object Pdfium {
    const val RELEASE = "chromium/8100"
    const val NDK_VERSION = "30.0.16248370"
    const val CMAKE_VERSION = "4.1.2"

    /** ABI -> asset name and SHA-256. */
    val assets = mapOf(
        "arm64-v8a" to ("pdfium-android-arm64.tgz" to "e9bab2929443d3d881166af9ec1d191c76ecdd37effb07a58743a7d7b507e149"),
        "armeabi-v7a" to ("pdfium-android-arm.tgz" to "876a8c44bb8d10b19ce816af11f1c527f101c4f714ef5670d885457b451846bb"),
        "x86_64" to ("pdfium-android-x64.tgz" to "b5cd96bc81c3414a84ce1f3eb928d6fe6981b13fe555f21bfa931bc478f51876"),
    )
}

@CacheableTask
abstract class FetchPdfiumTask : DefaultTask() {
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
            val actual = MessageDigest.getInstance("SHA-256").digest(tgz.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == sha) { "SHA-256 mismatch for $asset: expected $sha, got $actual" }
            fs.sync {
                from(archives.tarTree(archives.gzip(tgz)))
                into(File(out, abi))
            }
            fs.copy {
                from(File(out, "$abi/lib/libpdfium.so"))
                into(File(out, "jniLibs/$abi"))
            }
        }
        // License texts for PDFium and its bundled third-party code, shipped as assets for attribution.
        val first = assets.get().keys.first()
        fs.sync {
            from(File(out, "$first/LICENSE")) { into("pdfium") }
            from(File(out, "$first/licenses")) { into("pdfium/third_party") }
            into(File(out, "assets/third_party_licenses"))
        }
    }
}

/** Applies to `:core:pdf`: fetches PDFium, wires CMake, packages libpdfium.so and its licenses. */
class PdfiumConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val pdfiumDir = layout.buildDirectory.dir("pdfium")
        val fetch = tasks.register<FetchPdfiumTask>("fetchPdfium") {
            release.set(Pdfium.RELEASE)
            Pdfium.assets.forEach { (abi, spec) -> assets.put(abi, "${spec.first}|${spec.second}") }
            outputDir.set(pdfiumDir)
        }
        extensions.configure<LibraryExtension> {
            ndkVersion = Pdfium.NDK_VERSION
            defaultConfig {
                ndk { abiFilters += Pdfium.assets.keys }
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
                    version = Pdfium.CMAKE_VERSION
                }
            }
            sourceSets.getByName("main").jniLibs.directories.add(pdfiumDir.get().dir("jniLibs").asFile.path)
            sourceSets.getByName("main").assets.directories.add(pdfiumDir.get().dir("assets").asFile.path)
        }
        tasks.named("preBuild") { dependsOn(fetch) }
        Unit
    }
}
