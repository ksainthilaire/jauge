package io.jauge.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTextTest {

    @Test
    fun normalizeRemovesCaseAccentsLigaturesAndInclusiveMarks() {
        assertEquals("chargee marketing", SearchText.normalize("  Chargé·e   MARKETING "))
        assertEquals("ecully", SearchText.normalize("Écully"))
        assertEquals("coeur", SearchText.normalize("Cœur"))
    }

    @Test
    fun normalizeRemovesCombiningMarksOfDecomposedText() {
        assertEquals("velo", SearchText.normalize("Vélo"))
    }

    @Test
    fun matchesEveryWordInAnyFieldAndAnyOrder() {
        assertTrue(SearchText.matches("velo lyon", "Responsable CRM", "Vélo Ardent", "Lyon 9e"))
        assertTrue(SearchText.matches("CHARGÉE", "Chargé·e marketing digital"))
        assertFalse(SearchText.matches("velo paris", "Responsable CRM", "Vélo Ardent", "Lyon 9e"))
    }

    @Test
    fun blankQueryMatchesEverythingAndNullFieldsAreSkipped() {
        assertTrue(SearchText.matches("   ", "Traffic manager"))
        assertTrue(SearchText.matches("traffic", "Traffic manager", null))
    }
}
