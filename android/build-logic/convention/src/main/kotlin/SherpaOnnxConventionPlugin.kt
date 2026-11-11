import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Prebuilt sherpa-onnx (k2-fsa/sherpa-onnx) for Narrator Mode (Phase 6 M3): the single AAR ships the
 * Kotlin `OfflineTts` API (`classes.jar`) and the JNI libs (`libonnxruntime.so`, `libsherpa-onnx-jni.so`,
 * …) for all ABIs. Downloaded at build time, verified by SHA-256, never committed — the same pattern as
 * PDFium (ADR-0010). Update [SherpaOnnx.VERSION] + [SherpaOnnx.SHA256] together.
 *
 * AGP 9 rejects a local `.aar` as a dependency of a *library* module, so we extract the AAR here and wire
 * its `classes.jar` onto the compile classpath and its `jni/` into the module's jniLibs.
 */
object SherpaOnnx {
    const val VERSION = "2.0.1"
    const val SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
    val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
}

@CacheableTask
abstract class FetchSherpaOnnxTask : DefaultTask() {
    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        val aar = File(temporaryDir, "sherpa-onnx-${SherpaOnnx.VERSION}.aar")
        aar.parentFile.mkdirs()
        URI(url.get()).toURL().openStream().use { input -> aar.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(aar.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "SHA-256 mismatch for sherpa-onnx AAR: expected ${sha256.get()}, got $actual" }
        val tree = archives.zipTree(aar)
        fs.sync {
            from(tree)
            into(out)
        }
    }
}

/**
 * ONNX Runtime's C API headers at the version sherpa-onnx ships (1.28.2; MIT), for FlowFolio's own JNI bridge to that
 * runtime (OCR, ADR-0054). Its Java API cannot be used: its glue asks for the runtime's versioned symbols
 * (`OrtGetApiBase@VERS_1.28.0`), and no 1.28.2 Android build of it is published.
 */
object OrtHeaders {
    const val VERSION = "1.28.2"
    private const val BASE = "https://raw.githubusercontent.com/microsoft/onnxruntime/v$VERSION/include/onnxruntime/core/session/"
    val FILES = mapOf(
        "onnxruntime_c_api.h" to "b69a133143c0da61782b2b02cc8a620d21ac139231fd211719776d10385135c1",
        "onnxruntime_ep_c_api.h" to "db86df0f846b8d3bdbf429a5c76c5805293196575ca3c475878612591cd18e51",
        "onnxruntime_error_code.h" to "5ce3b054e798eced8d14f5b86e98692fd33470463f96194ce0700a2d53dd8721"
    ).mapKeys { (name, _) -> name }
    fun url(name: String) = BASE + name
}

@CacheableTask
abstract class FetchOrtHeadersTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        for ((name, sha) in OrtHeaders.FILES) {
            val bytes = URI(OrtHeaders.url(name)).toURL().openStream().use { it.readBytes() }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == sha) { "SHA-256 mismatch for $name: expected $sha, got $actual" }
            File(out, name).writeBytes(bytes)
        }
    }
}

/**
 * Applies to `:core:voice`: fetches and extracts the sherpa-onnx AAR, wiring classes + jniLibs; and builds the OCR
 * bridge (`src/main/cpp`) against the same `libonnxruntime.so`.
 */
class SherpaOnnxConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val out = layout.buildDirectory.dir("sherpa-onnx")
            val fetch = tasks.register<FetchSherpaOnnxTask>("fetchSherpaOnnx") {
                url.set(SherpaOnnx.URL)
                sha256.set(SherpaOnnx.SHA256)
                outputDir.set(out)
            }
            // Derive the jar from the task's output so the dependency carries the fetch task with it.
            dependencies.add("implementation", files(fetch.flatMap { it.outputDir.file("classes.jar") }))
            val headers = layout.buildDirectory.dir("ort-include")
            val fetchHeaders = tasks.register<FetchOrtHeadersTask>("fetchOrtHeaders") {
                version.set(OrtHeaders.VERSION)
                outputDir.set(headers)
            }
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("main").jniLibs.directories.add(out.get().dir("jni").asFile.path)
                ndkVersion = Pdfium.NDK_VERSION
                defaultConfig {
                    externalNativeBuild {
                        cmake {
                            arguments += "-DORT_INCLUDE=${headers.get().asFile.invariantSeparatorsPath}"
                            arguments += "-DSHERPA_JNI=${out.get().dir("jni").asFile.invariantSeparatorsPath}"
                        }
                    }
                }
                externalNativeBuild {
                    cmake {
                        path = file("src/main/cpp/CMakeLists.txt")
                    }
                }
            }
            tasks.named("preBuild") { dependsOn(fetch, fetchHeaders) }
        }
    }
}
