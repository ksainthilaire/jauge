package io.jauge.core.data

import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.domain.model.ReputationLink
import io.jauge.core.domain.model.ReputationSource
import io.jauge.core.domain.model.ReputationTone
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.network.payload.AdvicePayload
import io.jauge.core.network.payload.ReputationLinkPayload
import io.jauge.core.network.payload.ReputationPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdviceAndReputationMappingTest {

    @Test
    fun reputationKeepsOnlyKnownSourcesAndSafeLinks() {
        val reputation = ReputationPayload(
            summary = "  Ambiance correcte, salaires bas.  ",
            tone = "NEGATIVE",
            mentionCount = -3,
            sources = listOf("reddit", "linkedin"),
            links = listOf(
                ReputationLinkPayload("twitter", "https://x.com/i/status/1"),
                ReputationLinkPayload("reddit", "javascript:alert(1)"),
                ReputationLinkPayload("reddit", "http://www.reddit.com/r/a/2"),
                ReputationLinkPayload("mastodon", "https://mastodon.social/@a/3"),
            ),
        ).toDomainOrNull()
        assertNotNull(reputation)
        assertEquals("Ambiance correcte, salaires bas.", reputation.summary)
        assertEquals(ReputationTone.NEGATIVE, reputation.tone)
        assertEquals(0, reputation.mentionCount)
        assertEquals(listOf(ReputationSource.REDDIT, ReputationSource.X), reputation.sources)
        assertEquals(listOf(ReputationLink(ReputationSource.X, "https://x.com/i/status/1")), reputation.links)
    }

    @Test
    fun emptyReputationOrAdviceIsDropped() {
        assertNull(ReputationPayload(summary = " ").toDomainOrNull())
        assertNull(AdvicePayload(text = "").toDomainOrNull())
        assertEquals(ReputationTone.UNKNOWN, ReputationPayload(summary = "Rien de clair.", tone = "joyful").toDomainOrNull()?.tone)
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
    fun theOfflineVerdictHasAdviceAndReputation() = runTest {
        val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
        val analyses = AnalysisRepositoryImplementation(OfflineJaugeApi(OfflineDataset(), scenarios))
        val analysis = analyses.getAnalysis(OfflineDataset.MARKETING_ID).getOrNull()
        assertNotNull(analysis?.advice?.negotiationArgument)
        assertEquals(2, analysis?.reputation?.links?.size)
    }

    @Test
    fun searchLinksAreEncoded() {
        assertEquals("https://www.reddit.com/search/?q=Atelier%20Sorel%20%26%20Fils", ReputationSource.REDDIT.searchUrl(" Atelier Sorel & Fils "))
        assertEquals("https://x.com/search?f=live&q=C%C5%93ur", ReputationSource.X.searchUrl("Cœur"))
    }
}
