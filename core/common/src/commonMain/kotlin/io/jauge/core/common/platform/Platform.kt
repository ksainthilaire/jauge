package io.jauge.core.common.platform

/** Operating systems supported by the app. */
enum class Platform {
    /** Android phones. */
    ANDROID,

    /** iPhones. */
    IOS,
}

/** The platform the code is currently running on. */
expect val currentPlatform: Platform
