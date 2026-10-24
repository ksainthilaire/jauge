plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            api(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.retrofit)
            implementation(libs.retrofit.converter.kotlinxSerialization)
            implementation(libs.okhttp)
            implementation(libs.okhttp.loggingInterceptor)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.okhttp.mockWebServer)
            implementation(libs.retrofit)
            implementation(libs.retrofit.converter.kotlinxSerialization)
            implementation(libs.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
    }
}
