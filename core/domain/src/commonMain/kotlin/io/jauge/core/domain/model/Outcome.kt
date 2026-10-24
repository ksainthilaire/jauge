package io.jauge.core.domain.model

import io.jauge.core.domain.error.JaugeError

/**
 * Result of an operation of the domain: either a value or a [JaugeError]. Repositories never throw: they return an
 * [Outcome] so that every failure reaches the user interface as a friendly message.
 */
sealed interface Outcome<out T> {
    /**
     * The operation succeeded.
     *
     * @property value the result.
     */
    data class Success<out T>(val value: T) : Outcome<T>

    /**
     * The operation failed.
     *
     * @property error what went wrong, in user terms.
     */
    data class Failure(val error: JaugeError) : Outcome<Nothing>
}

/** The value of a success, or null. */
fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** The error of a failure, or null. */
fun Outcome<*>.errorOrNull(): JaugeError? = (this as? Outcome.Failure)?.error

/**
 * Transforms the value of a success.
 *
 * @param transform the transformation.
 */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/**
 * Chains an operation that also returns an [Outcome].
 *
 * @param transform the next operation.
 */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/**
 * Runs [action] on success and returns the same outcome.
 *
 * @param action the side effect.
 */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

/**
 * Runs [action] on failure and returns the same outcome.
 *
 * @param action the side effect.
 */
inline fun <T> Outcome<T>.onFailure(action: (JaugeError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}
