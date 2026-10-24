plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
    }
}
