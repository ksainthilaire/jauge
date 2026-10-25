package io.jauge.core.data

import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload

/**
 * [JaugeApi] for tests: answers like [delegate] and records the requests sent by the repositories.
 *
 * @param delegate API that answers, usually the offline one.
 */
class RecordingJaugeApi(private val delegate: JaugeApi) : JaugeApi by delegate {
    /** Bodies of `POST /v1/analyses`, in order. */
    val createdAnalyses = mutableListOf<CreateAnalysisRequestPayload>()

    /** `Idempotency-Key` headers of `POST /v1/analyses`, in order. */
    val idempotencyKeys = mutableListOf<String?>()

    /** Identifiers read with `GET /v1/analyses/{id}`, in order. */
    val polledAnalyses = mutableListOf<String>()

    override suspend fun createAnalysis(request: CreateAnalysisRequestPayload, idempotencyKey: String?): AnalysisPayload {
        createdAnalyses += request
        idempotencyKeys += idempotencyKey
        return delegate.createAnalysis(request, idempotencyKey)
    }

    override suspend fun getAnalysis(analysisId: String): AnalysisPayload {
        polledAnalyses += analysisId
        return delegate.getAnalysis(analysisId)
    }
}
