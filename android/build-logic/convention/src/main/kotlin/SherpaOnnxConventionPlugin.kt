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

/** Applies to `:core:voice`: fetches and extracts the sherpa-onnx AAR, wiring classes + jniLibs. */
class SherpaOnnxConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val out = layout.buildDirectory.dir("sherpa-onnx")
            val fetch = tasks.register<FetchSherpaOnnxTask>("fetchSherpaOnnx") {
                url.set(SherpaOnnx.URL)
                sha256.set(SherpaOnnx.SHA256)
                outputDir.set(out)
            }
            dependencies.add("implementation", files(out.map { it.file("classes.jar") }))
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("main").jniLibs.directories.add(out.get().dir("jni").asFile.path)
            }
            tasks.named("preBuild") { dependsOn(fetch) }
        }
    }
}
