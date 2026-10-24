plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.jauge.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.jauge.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            // R8: shrinks, optimizes and obfuscates the release build. The libraries ship their own rules
            // (Retrofit, OkHttp, kotlinx.serialization, RevenueCat, Firebase, Lottie); proguard-rules.pro adds the rest.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signing configuration in the repository: the upload key stays with the owner (docs/audit/securite.md).
        }
    }

    packaging {
        // Debugger support of kotlinx.coroutines: useless in the app.
        resources.excludes += "DebugProbesKt.bin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
}

// Firebase Cloud Messaging: google-services.json (never committed, see docs/supabase.md) is read only when present,
// so the app builds without it and simply has no push notifications.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
}
