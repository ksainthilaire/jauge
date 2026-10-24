plugins {
    alias(libs.plugins.jaugeComposeMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
        }
        androidMain.dependencies {
            implementation(libs.lottie.compose)
        }
        iosMain.dependencies {
            implementation(libs.compottie)
        }
    }
}

kotlin {
    sourceSets {
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.lottie.compose)
        }
    }
}

// Screenshot tests write their PNG files in docs/screenshots.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("jauge.screenshots.directory", rootProject.file("docs/screenshots").absolutePath)
}
