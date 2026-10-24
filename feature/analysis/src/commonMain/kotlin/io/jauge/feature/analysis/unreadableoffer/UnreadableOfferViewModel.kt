package io.jauge.feature.analysis.unreadableoffer

import androidx.lifecycle.ViewModel
import io.jauge.core.domain.model.OfferSource
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import io.jauge.feature.analysis.picker.PickedImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of the unreadable offer screen (mockup 10).
 *
 * @property imageBytes the image that could not be read, shown blurred, null when unknown.
 * @property recognizedCompany company that could still be read, null when none.
 * @property isImageUnreadable true when the new photo could not be opened.
 * @property startedRequestId new request to open on the reading screen.
 */
data class UnreadableOfferUiState(
    val imageBytes: ByteArray? = null,
    val recognizedCompany: String? = null,
    val isImageUnreadable: Boolean = false,
    val startedRequestId: String? = null,
)

/**
 * View model of the unreadable offer screen (mockup 10): shows what could be read and starts a new attempt.
 *
 * @param requestId request that failed.
 * @param recognizedCompany company that could still be read.
 * @param pendingAnalyses keeps the offers to analyze.
 */
class UnreadableOfferViewModel(
    requestId: String,
    recognizedCompany: String?,
    private val pendingAnalyses: PendingAnalysisStore,
) : ViewModel() {
    private val state = MutableStateFlow(
        UnreadableOfferUiState(
            imageBytes = (pendingAnalyses.get(requestId) as? OfferSource.Image)?.bytes,
            recognizedCompany = recognizedCompany?.takeIf { it.isNotBlank() },
        ),
    )

    /** Observed state of the screen. */
    val uiState: StateFlow<UnreadableOfferUiState> = state.asStateFlow()

    /**
     * A new photo came back from the camera.
     *
     * @param image the image, null when it could not be opened.
     */
    fun onImagePicked(image: PickedImage?) {
        if (image == null) {
            state.update { it.copy(isImageUnreadable = true) }
            return
        }
        val requestId = pendingAnalyses.put(OfferSource.Image(image.bytes, image.mimeType, image.origin))
        state.update { it.copy(isImageUnreadable = false, startedRequestId = requestId) }
    }

    /** The reading screen was opened. */
    fun onReadingOpened() {
        state.update { it.copy(startedRequestId = null) }
    }
}
