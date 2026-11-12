import com.android.build.api.dsl.LibraryExtension
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations

/**
 * Tesseract 5 and Leptonica, built from source with the NDK, for Arabic OCR (ADR-0054). No usable prebuilt exists
 * (Tesseract4Android is JitPack-only and its last release was June 2025), so the sources are fetched pinned, built
 * with CMake and the NDK toolchain, stripped, and handed to the module as jniLibs — the same fetch pattern PDFium and
 * sherpa-onnx use. Leptonica is built with no image codecs (raw pixels go in, so no libjpeg/png/tiff), and Tesseract
 * links Google's `cpu_features` (`ndk_compat`) statically, which Tesseract 5.5 requires on Android to detect NEON on
 * 32-bit ARM. All three are permissive: Tesseract and cpu_features Apache-2.0, Leptonica BSD-2-Clause.
 */
object Tesseract {
    const val VERSION = "5.5.4"
    const val SHA256 = "a8dbf8b8c8ee427ff6ad1d99116502feaf59440af1adb893caf60a6576826cf4"
    const val URL = "https://github.com/tesseract-ocr/tesseract/archive/refs/tags/$VERSION.tar.gz"

    const val LEPTONICA_VERSION = "1.87.0"
    const val LEPTONICA_SHA256 = "fa2b40c5caea96d1bb93a97486262aed8731b69ce25a84a6bf5d25323e33f631"
    const val LEPTONICA_URL = "https://github.com/DanBloomberg/leptonica/archive/refs/tags/$LEPTONICA_VERSION.tar.gz"

    const val CPU_FEATURES_VERSION = "0.9.0"
    const val CPU_FEATURES_SHA256 = "bdb3484de8297c49b59955c3b22dba834401bc2df984ef5cfc17acbe69c5018e"
    const val CPU_FEATURES_URL = "https://github.com/google/cpu_features/archive/refs/tags/v$CPU_FEATURES_VERSION.tar.gz"

    /** The ABIs PDFium ships; a build that has pdfium then has tesseract too. */
    val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
}

/**
 * Fetches the three pinned sources and builds `libleptonica.so` + `libtesseract.so` for every ABI into
 * `outputDir/jniLibs/<abi>/`. Cacheable: the URLs and SHA-256s are its inputs, the `.so` files its outputs.
 */
@CacheableTask
abstract class BuildTesseractTask : DefaultTask() {
    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val fs: FileSystemOperations

    @get:Inject abstract val execOps: ExecOperations

    @get:Input abstract val abis: ListProperty<String>

    @get:Input abstract val sdkDir: Property<String>

    @get:Input abstract val ndkVersion: Property<String>

    @get:Input abstract val cmakeVersion: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val sdk = File(sdkDir.get())
        val cmake = File(sdk, "cmake/${cmakeVersion.get()}/bin/cmake").also { check(it.isFile) { "no cmake at $it" } }
        val ninja = File(sdk, "cmake/${cmakeVersion.get()}/bin/ninja").also { check(it.isFile) { "no ninja at $it" } }
        val ndk = File(sdk, "ndk/${ndkVersion.get()}").also { check(it.isDirectory) { "no NDK at $it" } }
        val toolchain = File(ndk, "build/cmake/android.toolchain.cmake")
        val host = when {
            System.getProperty("os.name").startsWith("Windows") -> "windows-x86_64"
            System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
            else -> "linux-x86_64"
        }
        val strip = File(ndk, "toolchains/llvm/prebuilt/$host/bin/llvm-strip")
            .also { check(it.isFile) { "no llvm-strip at $it" } }

        val src = File(temporaryDir, "src")
        val leptonica = fetch("leptonica", Tesseract.LEPTONICA_URL, Tesseract.LEPTONICA_SHA256, src)
        val cpuFeatures = fetch("cpu_features", Tesseract.CPU_FEATURES_URL, Tesseract.CPU_FEATURES_SHA256, src)
        val tesseract = fetch("tesseract", Tesseract.URL, Tesseract.SHA256, src)

        val out = outputDir.get().asFile
        for (abi in abis.get()) {
            val install = File(temporaryDir, "install-$abi")
            val common = listOf(
                "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=android-29",
                "-DCMAKE_MAKE_PROGRAM=$ninja",
                "-DCMAKE_BUILD_TYPE=Release"
            )
            // Leptonica: shared, no image codecs, no programs.
            run(cmake, temporaryDir,
                "-S", leptonica.absolutePath, "-B", File(temporaryDir, "leptonica-$abi").absolutePath, "-G", "Ninja",
                *common.toTypedArray(),
                "-DBUILD_SHARED_LIBS=ON", "-DBUILD_PROG=OFF", "-DSW_BUILD=OFF",
                "-DENABLE_ZLIB=OFF", "-DENABLE_PNG=OFF", "-DENABLE_GIF=OFF", "-DENABLE_JPEG=OFF",
                "-DENABLE_TIFF=OFF", "-DENABLE_WEBP=OFF", "-DENABLE_OPENJPEG=OFF",
                "-DCMAKE_INSTALL_PREFIX=${File(install, "leptonica")}"
            )
            run(cmake, temporaryDir, "--build", File(temporaryDir, "leptonica-$abi").absolutePath)
            run(cmake, temporaryDir, "--install", File(temporaryDir, "leptonica-$abi").absolutePath)

            // cpu_features: the ndk_compat library Tesseract needs on Android, statically linked.
            run(cmake, temporaryDir,
                "-S", cpuFeatures.absolutePath, "-B", File(temporaryDir, "cpu-$abi").absolutePath, "-G", "Ninja",
                *common.toTypedArray(),
                "-DBUILD_TESTING=OFF", "-DCMAKE_INSTALL_PREFIX=${File(install, "cpu_features")}"
            )
            run(cmake, temporaryDir, "--build", File(temporaryDir, "cpu-$abi").absolutePath)
            run(cmake, temporaryDir, "--install", File(temporaryDir, "cpu-$abi").absolutePath)

            // Tesseract: shared, no training tools, no OpenMP, no TIFF/archive/curl, LSTM-only for the model used.
            run(cmake, temporaryDir,
                "-S", tesseract.absolutePath, "-B", File(temporaryDir, "tesseract-$abi").absolutePath, "-G", "Ninja",
                *common.toTypedArray(),
                "-DANDROID_STL=c++_shared",
                "-DBUILD_SHARED_LIBS=ON", "-DBUILD_TRAINING_TOOLS=OFF", "-DBUILD_TESTS=OFF",
                "-DINSTALL_CONFIGS=OFF", "-DGRAPHICS_DISABLED=ON", "-DOPENMP_BUILD=OFF",
                "-DDISABLE_TIFF=ON", "-DDISABLE_ARCHIVE=ON", "-DDISABLE_CURL=ON",
                "-DUSE_SYSTEM_ICU=OFF", "-DENABLE_LTO=OFF", "-DENABLE_NATIVE=OFF",
                "-DLEPT_TIFF_RESULT=1",
                "-DLeptonica_DIR=${File(File(install, "leptonica"), "lib/cmake/leptonica")}",
                "-DCpuFeaturesNdkCompat_DIR=${File(File(install, "cpu_features"), "lib/cmake/CpuFeaturesNdkCompat")}",
                "-DCMAKE_INSTALL_PREFIX=${File(install, "tesseract")}"
            )
            run(cmake, temporaryDir, "--build", File(temporaryDir, "tesseract-$abi").absolutePath)
            run(cmake, temporaryDir, "--install", File(temporaryDir, "tesseract-$abi").absolutePath)

            val jni = File(out, "jniLibs/$abi").apply { mkdirs() }
            copy(File(install, "leptonica/lib/libleptonica.so"), File(jni, "libleptonica.so"))
            copy(File(install, "tesseract/lib/libtesseract.so"), File(jni, "libtesseract.so"))
            // Tesseract is built against the shared libc++; the NDK's own copy ships with it (the app's other native
            // libs use the static STL, so none bundled it before).
            val triple = when (abi) {
                "arm64-v8a" -> "aarch64-linux-android"
                "armeabi-v7a" -> "arm-linux-androideabi"
                "x86_64" -> "x86_64-linux-android"
                else -> error("unexpected ABI $abi")
            }
            copy(
                File(ndk, "toolchains/llvm/prebuilt/$host/sysroot/usr/lib/$triple/libc++_shared.so"),
                File(jni, "libc++_shared.so")
            )
            run(strip, temporaryDir, "--strip-debug", File(jni, "libleptonica.so").absolutePath, File(jni, "libtesseract.so").absolutePath)

            // The C API headers the bridge compiles against (identical across ABIs; copied from the last one).
            if (abi == abis.get().last()) {
                fs.copy {
                    from(File(install, "tesseract/include"))
                    into(File(out, "include"))
                }
            }
        }
    }

    /** Downloads [name]'s tarball, checks its SHA-256, extracts it, and returns its top-level directory. */
    private fun fetch(name: String, url: String, sha256: String, into: File): File {
        val tgz = File(temporaryDir, "$name.tar.gz")
        if (!tgz.isFile) {
            URI(url).toURL().openStream().use { input -> tgz.outputStream().use { input.copyTo(it) } }
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(tgz.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256) { "SHA-256 mismatch for $name: expected $sha256, got $actual" }
        fs.copy {
            from(archives.tarTree(archives.gzip(tgz)))
            into(into)
        }
        return into.listFiles()!!.single { it.isDirectory && it.name.startsWith("$name-") }
    }

    private fun copy(from: File, to: File) {
        check(from.isFile) { "built library missing: $from" }
        from.copyTo(to, overwrite = true)
    }

    private fun run(binary: File, workingDir: File, vararg args: String) {
        execOps.exec {
            commandLine(binary.absolutePath, *args)
            this.workingDir = workingDir
        }
    }
}

/** Applies to `:core:ocr`: builds Tesseract and Leptonica, and builds the JNI bridge against them. */
class TesseractConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val sdk = sdkDir(target)
        val tesseractDir = layout.buildDirectory.dir("tesseract")
        val build = tasks.register<BuildTesseractTask>("buildTesseract") {
            abis.set(Tesseract.ABIS)
            sdkDir.set(sdk.absolutePath)
            ndkVersion.set(Pdfium.NDK_VERSION)
            cmakeVersion.set(Pdfium.CMAKE_VERSION)
            outputDir.set(tesseractDir)
        }
        extensions.configure<LibraryExtension> {
            ndkVersion = Pdfium.NDK_VERSION
            defaultConfig {
                ndk { abiFilters += Tesseract.ABIS }
                externalNativeBuild {
                    cmake {
                        arguments += "-DTESSERACT_ROOT=${tesseractDir.get().asFile.invariantSeparatorsPath}"
                    }
                }
            }
            externalNativeBuild {
                cmake {
                    path = file("src/main/cpp/CMakeLists.txt")
                    version = Pdfium.CMAKE_VERSION
                }
            }
            sourceSets.getByName("main").jniLibs.directories.add(tesseractDir.get().dir("jniLibs").asFile.path)
        }
        tasks.named("preBuild") { dependsOn(build) }
        Unit
    }

    /** The Android SDK, from the environment or local.properties, as Gradle itself finds it. */
    private fun sdkDir(project: Project): File {
        val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!env.isNullOrBlank()) return File(env)
        val local = project.rootProject.file("local.properties")
        if (local.isFile) {
            val entry = local.readLines().firstOrNull { it.startsWith("sdk.dir=") }
            entry?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        }
        throw GradleException("Android SDK not found: set ANDROID_HOME or sdk.dir in local.properties")
    }
}
