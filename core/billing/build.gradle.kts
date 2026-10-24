plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(libs.revenueCat.purchases.core)
        }
    }
}
