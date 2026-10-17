import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library defaults for core and feature modules (ADR-0012). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("flowfolio.quality")
        extensions.configure<LibraryExtension> { configureAndroidLibrary(this) }
        configureKotlinAndroid()
    }
}
