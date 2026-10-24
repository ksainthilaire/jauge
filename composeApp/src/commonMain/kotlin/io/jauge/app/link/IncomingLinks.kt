package io.jauge.app.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Links opened from outside the app (Android App Links, iOS Universal Links), such as
 * `https://jauge.io/a/{analysisId}`. The platform entry points call [open]; `JaugeApp` navigates to the matching
 * screen and calls [consume]. A link opened before the user interface exists waits here.
 */
object IncomingLinks {
    private val pendingLink = MutableStateFlow<String?>(null)

    /** The link waiting to be opened, null when there is none. */
    val pending: StateFlow<String?> = pendingLink.asStateFlow()

    /**
     * Receives a link from the platform.
     *
     * @param url the opened link; null or blank values are ignored.
     */
    fun open(url: String?) {
        if (!url.isNullOrBlank()) pendingLink.value = url
    }

    /** Marks the pending link as handled. */
    fun consume() {
        pendingLink.value = null
    }
}
