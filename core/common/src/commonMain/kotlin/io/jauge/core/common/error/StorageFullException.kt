package io.jauge.core.common.error

/**
 * Thrown by platform code when a file cannot be written because the device has no free space left. The data layer
 * maps it to a friendly "storage full" message.
 *
 * @param cause the original exception, if any.
 */
class StorageFullException(cause: Throwable? = null) : Exception("Storage full", cause)
