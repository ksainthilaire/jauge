package io.jauge.core.notifications

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformPushModule(): Module = module {
    single<PushService> { FirebasePushService(androidContext()) }
}
