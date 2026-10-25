package io.jauge.core.textrecognition

import io.jauge.core.domain.service.TextRecognizer
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformTextRecognitionModule(): Module = module {
    single<TextRecognizer> { VisionTextRecognizer() }
}
