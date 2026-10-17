plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
    alias(libs.plugins.flowfolio.hilt)
}

android {
    namespace = "com.flowfolio.feature.library"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
