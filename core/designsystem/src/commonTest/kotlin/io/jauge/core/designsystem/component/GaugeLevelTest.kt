package io.jauge.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class GaugeLevelTest {

    @Test
    fun scoresMapToTheThreeLevels() {
        assertEquals(GaugeLevel.CALM, GaugeLevel.fromScore(0))
        assertEquals(GaugeLevel.CALM, GaugeLevel.fromScore(33))
        assertEquals(GaugeLevel.DISPUTED, GaugeLevel.fromScore(34))
        assertEquals(GaugeLevel.DISPUTED, GaugeLevel.fromScore(66))
        assertEquals(GaugeLevel.CROWDED, GaugeLevel.fromScore(67))
        assertEquals(GaugeLevel.CROWDED, GaugeLevel.fromScore(100))
    }

    @Test
    fun outOfRangeScoresAreClamped() {
        assertEquals(GaugeLevel.CALM, GaugeLevel.fromScore(-5))
        assertEquals(GaugeLevel.CROWDED, GaugeLevel.fromScore(140))
    }
}
