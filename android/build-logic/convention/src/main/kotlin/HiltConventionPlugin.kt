import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt dependency injection with KSP code generation (ADR-0007). */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}
