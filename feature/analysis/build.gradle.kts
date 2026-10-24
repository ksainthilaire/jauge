plugins {
    alias(libs.plugins.jaugeFeature)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.compose.ui.test.junit4)
        }
    }
}

// Screenshot tests (Roborazzi): `./gradlew :feature:<name>:testAndroidHostTest -Djauge.screenshots=record`
// writes the PNG files to build/screenshots; they are copied to docs/screenshots after review.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", System.getProperty("jauge.screenshots") == "record")
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}
