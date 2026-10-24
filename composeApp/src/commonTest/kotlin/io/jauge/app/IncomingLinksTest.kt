package io.jauge.app

import io.jauge.app.link.IncomingLinks
import io.jauge.feature.share.link.AnalysisLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingLinksTest {

    @Test
    fun aLinkWaitsUntilItIsConsumed() {
        IncomingLinks.open("https://jauge.io/a/analysis-marketing")

        assertEquals("analysis-marketing", AnalysisLink.analysisIdOrNull(IncomingLinks.pending.value))
        IncomingLinks.consume()
        assertNull(IncomingLinks.pending.value)
    }

    @Test
    fun blankLinksAreIgnored() {
        IncomingLinks.consume()
        IncomingLinks.open(" ")
        IncomingLinks.open(null)

        assertNull(IncomingLinks.pending.value)
    }
}
