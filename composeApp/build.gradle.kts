plugins {
    alias(libs.plugins.jaugeComposeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.network)
            implementation(projects.core.billing)
            implementation(projects.core.notifications)
            implementation(projects.core.authentication)

            implementation(projects.feature.onboarding)
            implementation(projects.feature.analysis)
            implementation(projects.feature.verdict)
            implementation(projects.feature.twins)
            implementation(projects.feature.share)
            implementation(projects.feature.paywall)
            implementation(projects.feature.account)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
