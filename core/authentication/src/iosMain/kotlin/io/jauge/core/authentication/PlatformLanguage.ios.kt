package io.jauge.core.authentication

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** Language code of the current locale of the device (the one the app resources follow). */
internal actual fun platformLanguageTag(): String? = NSLocale.currentLocale.languageCode.ifBlank { null }
