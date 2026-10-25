package io.jauge.core.authentication

import java.util.Locale

/** Language tag of the default locale of the device (the one the app resources follow). */
internal actual fun platformLanguageTag(): String? = Locale.getDefault().toLanguageTag().ifBlank { null }
