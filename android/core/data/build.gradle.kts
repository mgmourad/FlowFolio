plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.hilt)
}

android {
    namespace = "com.flowfolio.core.data"
}

// Reuse :core:pdf's generated robustness PDFs as fixtures for on-device import tests.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("androidTest").assets.directories.add("../pdf/src/androidTest/assets")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    api(projects.core.epub)
    implementation(projects.core.datastore)
    api(projects.core.pdf)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
