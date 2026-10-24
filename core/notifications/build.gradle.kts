plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.authentication)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
    }
}

// Android resources hold the name of the notification channel shown in the system settings.
kotlin {
    extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        androidResources {
            enable = true
        }
    }
}
