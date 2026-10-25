package io.jauge.core.textrecognition

import io.jauge.core.domain.service.TextRecognizer
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module of `core:textrecognition`: the [TextRecognizer] of the platform, which reads the text of screenshots
 * and photos on the phone (ML Kit on Android, Apple Vision on iOS).
 */
val textRecognitionModule: Module = module {
    includes(platformTextRecognitionModule())
}

/** Declares the [TextRecognizer] of the platform. */
internal expect fun platformTextRecognitionModule(): Module
