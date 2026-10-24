plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
        }
    }
}
