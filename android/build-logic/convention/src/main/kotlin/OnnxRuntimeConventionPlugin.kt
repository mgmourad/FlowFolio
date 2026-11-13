import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
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
 * Microsoft's ONNX Runtime for Android (MIT), the runtime OCR runs on (ADR-0054, ADR-0059). Narrator Mode used to
 * bring its own copy inside sherpa-onnx; with that gone (ADR-0059) the runtime comes from Microsoft's published AAR.
 * Only its native library and C headers are used, through FlowFolio's own JNI bridge (`OrtBridge`): the AAR's Java
 * API is left out. Downloaded at build time and pinned by SHA-256, never committed, as PDFium is (ADR-0010). The
 * version matches the JVM tests' `onnxruntime` in libs.versions.toml; change [VERSION] and [SHA256] together.
 */
object OnnxRuntime {
    const val VERSION = "1.28.0"
    const val SHA256 = "f351a0638696f54b35184290dbc001d66daae17281ad0b548d2c70347d53b8a9"
    val URL = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/$VERSION/" +
        "onnxruntime-android-$VERSION.aar"

    /** The app's ABIs (the same as PDFium's): the AAR's x86 build is left out. */
    val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
}

@CacheableTask
abstract class FetchOnnxRuntimeTask : DefaultTask() {
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
        val aar = File(temporaryDir, "onnxruntime-android-${OnnxRuntime.VERSION}.aar")
        aar.parentFile.mkdirs()
        URI(url.get()).toURL().openStream().use { input -> aar.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(aar.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "SHA-256 mismatch for the ONNX Runtime AAR: expected ${sha256.get()}, got $actual" }
        val tree = archives.zipTree(aar)
        fs.sync {
            from(tree) {
                include("headers/*.h")
                OnnxRuntime.ABIS.forEach { include("jni/$it/libonnxruntime.so") }
            }
            into(out)
        }
    }
}

/** Applies to `:core:voice`: fetches the runtime and builds the OCR bridge (`src/main/cpp`) against it. */
class OnnxRuntimeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val out = layout.buildDirectory.dir("onnxruntime")
            val fetch = tasks.register<FetchOnnxRuntimeTask>("fetchOnnxRuntime") {
                url.set(OnnxRuntime.URL)
                sha256.set(OnnxRuntime.SHA256)
                outputDir.set(out)
            }
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("main").jniLibs.directories.add(out.get().dir("jni").asFile.path)
                ndkVersion = Pdfium.NDK_VERSION
                defaultConfig {
                    externalNativeBuild {
                        cmake {
                            arguments += "-DORT_INCLUDE=${out.get().dir("headers").asFile.invariantSeparatorsPath}"
                            arguments += "-DORT_JNI=${out.get().dir("jni").asFile.invariantSeparatorsPath}"
                        }
                    }
                }
                externalNativeBuild {
                    cmake {
                        path = file("src/main/cpp/CMakeLists.txt")
                    }
                }
            }
            tasks.named("preBuild") { dependsOn(fetch) }
        }
    }
}
