package io.jauge.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Coroutine dispatchers used by the app. Injected so that tests can replace them with a test dispatcher.
 */
interface DispatcherProvider {
    /** Dispatcher for user interface work. */
    val main: CoroutineDispatcher

    /** Dispatcher for blocking input and output (network, files). */
    val io: CoroutineDispatcher

    /** Dispatcher for CPU heavy work. */
    val default: CoroutineDispatcher
}

/** Production dispatchers backed by [Dispatchers]. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
