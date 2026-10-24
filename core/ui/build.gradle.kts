plugins {
    alias(libs.plugins.jaugeComposeMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
        }
    }
}
