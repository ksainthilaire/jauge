plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.network)
            implementation(projects.core.billing)
            implementation(projects.core.notifications)
        }
    }
}
