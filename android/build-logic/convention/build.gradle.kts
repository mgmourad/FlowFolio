plugins {
    `kotlin-dsl`
}

group = "com.flowfolio.buildlogic"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "flowfolio.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "flowfolio.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "flowfolio.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "flowfolio.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("pdfium") {
            id = "flowfolio.pdfium"
            implementationClass = "PdfiumConventionPlugin"
        }
        register("sherpaOnnx") {
            id = "flowfolio.sherpa-onnx"
            implementationClass = "SherpaOnnxConventionPlugin"
        }
        register("hilt") {
            id = "flowfolio.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("quality") {
            id = "flowfolio.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
