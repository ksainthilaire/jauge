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
            // Bundled model: works offline, no Google Play service and no paid Google API.
            implementation(libs.mlkit.textRecognition)
        }
    }
}
