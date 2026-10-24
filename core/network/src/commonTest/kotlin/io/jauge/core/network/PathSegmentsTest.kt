package io.jauge.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class PathSegmentsTest {

    @Test
    fun plainIdentifiersAreKept() {
        assertEquals("analysis-marketing_42", pathSegment("analysis-marketing_42"))
    }

    @Test
    fun charactersThatChangeTheUrlAreEncoded() {
        assertEquals("%2E%2E%2Faccount", pathSegment("../account"))
        assertEquals("a%3Fb%3D1%23c", pathSegment("a?b=1#c"))
        assertEquals("%C3%A9t%C3%A9", pathSegment("été"))
    }
}
