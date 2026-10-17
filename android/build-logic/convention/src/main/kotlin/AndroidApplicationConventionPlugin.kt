import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application defaults. AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android plugin. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("flowfolio.quality")
        extensions.configure<ApplicationExtension> { configureAndroidApplication(this) }
        configureKotlinAndroid()
    }
}
