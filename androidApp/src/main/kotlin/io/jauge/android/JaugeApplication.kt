package io.jauge.android

import android.app.Application
import io.jauge.app.di.startJaugeKoin
import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.common.platform.Platform
import org.koin.android.ext.koin.androidContext

/** Android application: starts Koin with the environment of this build. */
class JaugeApplication : Application() {

    /** Starts dependency injection before any screen is created. */
    override fun onCreate() {
        super.onCreate()
        startJaugeKoin(
            environment = AppEnvironment.fromBuildConfiguration(
                platform = Platform.ANDROID,
                isDebugBuild = BuildConfig.DEBUG,
            ),
        ) {
            androidContext(this@JaugeApplication)
        }
    }
}
