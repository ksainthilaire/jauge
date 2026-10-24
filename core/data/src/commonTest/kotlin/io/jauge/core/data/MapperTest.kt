package io.jauge.core.data

import io.jauge.core.data.mapper.toDomain
import io.jauge.core.data.mapper.toDomainAnalysis
import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.data.mapper.toProgress
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.network.JaugeJson
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisFailurePayload
import io.jauge.core.network.payload.AnalysisSummaryPayload
import io.jauge.core.network.payload.SalaryInsightPayload
import io.jauge.core.network.payload.SalaryRangePayload
import io.jauge.core.network.payload.TrendInsightPayload
import io.jauge.core.network.payload.TrendPointPayload
import io.jauge.core.network.parseApiErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MapperTest {

    @Test
    fun invalidValuesBecomeEmptyFieldsRatherThanWrongValues() {
        val salary = SalaryInsightPayload(
            areaName = "Lyon",
            offerCount = 412,
            scaleMinimum = 30,
            scaleMaximum = 48,
            middleHalf = SalaryRangePayload(33, 44),
            median = 37,
            offerRange = SalaryRangePayload(45, 30),
            negotiationTarget = -1,
        ).toDomain()
        assertNull(salary.offerRange)
        assertNull(salary.negotiationTargetThousands)
        assertNull(salary.position)
    }

    @Test
    fun unreadableItemsAreSkipped() {
        assertNull(AnalysisSummaryPayload("id", "not a date", "Job", null, "Lyon", 50).toDomainOrNull())
        val trend = TrendInsightPayload(listOf(TrendPointPayload("2026-09", 10), TrendPointPayload("??", 12)), 5).toDomain()
        assertEquals(1, trend.points.size)
    }

    @Test
    fun incompleteAnalysisIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            AnalysisPayload(id = "x", status = AnalysisPayload.STATUS_COMPLETED, createdAt = "2026-10-23T08:00:00Z").toDomainAnalysis()
        }
    }

    @Test
    fun failedAnalysisCarriesItsReason() {
        val progress = AnalysisPayload(
            id = "x",
            status = AnalysisPayload.STATUS_FAILED,
            createdAt = "2026-10-23T08:00:00Z",
            failure = AnalysisFailurePayload("unreadable_offer", "Maison Odier"),
        ).toProgress()
        assertEquals(AnalysisProgress.Failed(JaugeError.UnreadableOffer("Maison Odier")), progress)
    }

    @Test
    fun unknownJsonFieldsAreIgnoredAndErrorCodesParsed() {
        val json = """{"id":"a","status":"processing","createdAt":"2026-10-23T08:00:00Z","futureField":true,"progressPercent":62}"""
        assertEquals(62, JaugeJson.decodeFromString(AnalysisPayload.serializer(), json).progressPercent)
        assertEquals("quota_reached", parseApiErrorCode("""{"error":{"code":"quota_reached","message":"Weekly quota used"}}"""))
        assertNull(parseApiErrorCode("<html>Bad gateway</html>"))
    }
}
