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

// Screenshot tests (Roborazzi) write their PNG files to build/screenshots, or to docs/screenshots with
// ./gradlew :feature:twins:testAndroidHostTest -Pjauge.screenshots
val screenshotsDirectory = if (providers.gradleProperty("jauge.screenshots").isPresent) {
    rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath
} else {
    layout.buildDirectory.dir("screenshots").get().asFile.absolutePath
}
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("jauge.screenshots.directory", screenshotsDirectory)
}
