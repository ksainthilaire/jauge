plugins {
    `kotlin-dsl`
}

group = "io.jauge.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatformLibrary") {
            id = "jauge.kotlin.multiplatform.library"
            implementationClass = "KotlinMultiplatformLibraryConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "jauge.compose.multiplatform"
            implementationClass = "ComposeMultiplatformConventionPlugin"
        }
        register("feature") {
            id = "jauge.feature"
            implementationClass = "FeatureConventionPlugin"
        }
    }
}
