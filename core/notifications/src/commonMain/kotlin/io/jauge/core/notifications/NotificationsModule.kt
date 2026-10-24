package io.jauge.core.notifications

import io.jauge.core.authentication.SupabaseClientProvider
import io.jauge.core.domain.repository.NotificationRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module of `core:notifications`: the platform [PushService] and the [NotificationRepository] (Supabase tables
 * and Realtime when Supabase is configured, [SimulatedNotificationRepository] otherwise). Requires
 * `authenticationModule`.
 */
val notificationsModule = module {
    includes(platformPushModule())
    single<NotificationRepository> {
        val client = get<SupabaseClientProvider>().client
        if (client != null) {
            SupabaseNotificationRepository(client = client, authentication = get(), pushService = get())
        } else {
            SimulatedNotificationRepository()
        }
    }
}

/** Declares the [PushService] of the platform: Firebase Cloud Messaging on Android, APNs on iOS. */
internal expect fun platformPushModule(): Module
