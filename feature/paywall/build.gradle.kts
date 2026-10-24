plugins {
    alias(libs.plugins.jaugeFeature)
}

kotlin {
    sourceSets {
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}

// Screenshot tests write their PNG files in docs/screenshots.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("jauge.screenshots.directory", rootProject.file("docs/screenshots").absolutePath)
}
