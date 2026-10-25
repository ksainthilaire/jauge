package io.jauge.core.data

import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.network.JaugeJson
import io.jauge.core.network.payload.AdvicePayload
import io.jauge.core.network.payload.AnalysisPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdviceMappingTest {

    @Test
    fun emptyAdviceIsDropped() {
        assertNull(AdvicePayload(text = "").toDomainOrNull())
        assertNull(AdvicePayload(text = "   ").toDomainOrNull())
    }

    @Test
    fun adviceIsCleaned() {
        val advice = AdvicePayload(
            text = " Postule avant jeudi. ",
            negotiationArgument = "  ",
            missingSkills = listOf("SQL", " ", "SQL", "Figma", "HubSpot", "Python", "Excel"),
        ).toDomainOrNull()
        assertEquals("Postule avant jeudi.", advice?.text)
        assertNull(advice?.negotiationArgument)
        assertEquals(listOf("SQL", "Figma", "HubSpot", "Python"), advice?.missingSkills)
    }

    @Test
    fun aReputationSentByAnOlderServerIsIgnored() {
        val json = """{"id":"a1","status":"completed","createdAt":"2026-10-23T08:00:00Z","result":{"score":78,""" +
            """"percentileInRegion":84,"regionName":"Rhône","applicants":{"estimate":210,"low":160,"high":260},""" +
            """"reliability":"medium","reputation":{"summary":"Bonne ambiance.","tone":"positive","mentionCount":3,""" +
            """"sources":["reddit"],"links":[]}}}"""
        val payload = JaugeJson.decodeFromString(AnalysisPayload.serializer(), json)
        assertEquals(78, payload.result?.score)
    }

    @Test
    fun theOfflineVerdictHasAdvice() = runTest {
        val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
        val analyses = AnalysisRepositoryImplementation(OfflineJaugeApi(OfflineDataset(), scenarios))
        val analysis = analyses.getAnalysis(OfflineDataset.MARKETING_ID).getOrNull()
        assertNotNull(analysis?.advice?.negotiationArgument)
        assertEquals(listOf("HubSpot", "Figma"), analysis?.advice?.missingSkills)
    }
}
