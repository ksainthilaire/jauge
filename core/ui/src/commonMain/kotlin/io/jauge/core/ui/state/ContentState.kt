package io.jauge.core.ui.state

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome

/**
 * Observed state of a screen that loads data, exposed by view models as a `StateFlow<ContentState<T>>`.
 *
 * - first load: [isLoading] true and no [content] (show skeletons);
 * - pull to refresh: [isRefreshing] true, the previous [content] stays on screen;
 * - failure without content: [error] set (show a friendly error);
 * - failure with content: [error] set and [content] kept (show a short message, keep the data).
 *
 * @property content loaded content, null until the first success.
 * @property isLoading true during the first load.
 * @property isRefreshing true during a pull to refresh.
 * @property error last error, null after a success.
 */
data class ContentState<out T>(
    val content: T? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: JaugeError? = null,
) {
    /** True when the error should replace the whole screen. */
    val showsFullScreenError: Boolean
        get() = error != null && content == null && !isLoading
}

/** State while a refresh starts: keeps the content and shows the refresh indicator. */
fun <T> ContentState<T>.refreshing(): ContentState<T> =
    copy(isRefreshing = content != null, isLoading = content == null, error = null)

/**
 * State after an [Outcome]: the new content on success, the error (and the previous content) on failure.
 *
 * @param outcome result of the load.
 */
fun <T> ContentState<T>.reduce(outcome: Outcome<T>): ContentState<T> = when (outcome) {
    is Outcome.Success -> ContentState(content = outcome.value, isLoading = false, isRefreshing = false, error = null)
    is Outcome.Failure -> copy(isLoading = false, isRefreshing = false, error = outcome.error)
}
