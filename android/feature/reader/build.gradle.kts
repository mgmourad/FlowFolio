plugins {
    alias(libs.plugins.flowfolio.android.library)
    alias(libs.plugins.flowfolio.android.compose)
    alias(libs.plugins.flowfolio.hilt)
}

android {
    namespace = "com.flowfolio.feature.reader"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.reflow)
    implementation(projects.core.designsystem)
    implementation(projects.core.pagecurl)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
