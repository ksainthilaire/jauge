package io.jauge.core.notifications

import io.jauge.core.domain.repository.NotificationRepository
import org.koin.dsl.module

/**
 * Koin module of `core:notifications`: the [NotificationRepository] of the app. Supabase (tables, Realtime) and the
 * platform push service when configured, [SimulatedNotificationRepository] otherwise.
 */
val notificationsModule = module {
    single<NotificationRepository> { SimulatedNotificationRepository() }
}
