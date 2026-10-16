import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Formatting (Spotless + ktlint) and static analysis (detekt) for every module. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.diffplug.spotless")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.version("ktlint"))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.version("ktlint"))
            }
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
        }
    }
}
