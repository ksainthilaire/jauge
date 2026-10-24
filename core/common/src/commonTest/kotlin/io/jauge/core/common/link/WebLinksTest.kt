package io.jauge.core.common.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebLinksTest {

    @Test
    fun httpsWebPagesAreAccepted() {
        assertTrue(WebLinks.isSafeWebLink("https://www.welcometothejungle.com/fr/jobs/123?source=jauge#apply"))
        assertTrue(WebLinks.isSafeWebLink("https://play.google.com/store/account/subscriptions"))
        assertTrue(WebLinks.isSafeWebLink("HTTPS://apps.apple.com:443/account/subscriptions"))
        assertEquals("https://jauge.io", WebLinks.safeWebLinkOrNull("  https://jauge.io "))
    }

    @Test
    fun dangerousOrMalformedLinksAreRefused() {
        assertFalse(WebLinks.isSafeWebLink(null))
        assertFalse(WebLinks.isSafeWebLink(""))
        assertFalse(WebLinks.isSafeWebLink("http://example.com"))
        assertFalse(WebLinks.isSafeWebLink("javascript:alert(1)"))
        assertFalse(WebLinks.isSafeWebLink("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(WebLinks.isSafeWebLink("file:///data/data/io.jauge.app/shared_prefs/session.xml"))
        assertFalse(WebLinks.isSafeWebLink("io.jauge.app://login-callback?code=1"))
        assertFalse(WebLinks.isSafeWebLink("https://user:password@example.com/"))
        assertFalse(WebLinks.isSafeWebLink("https://example.com\\@evil.example/"))
        assertFalse(WebLinks.isSafeWebLink("https:///path-only"))
        assertFalse(WebLinks.isSafeWebLink("https://exa mple.com/"))
        assertFalse(WebLinks.isSafeWebLink("https://example.com/" + "x".repeat(2100)))
        assertNull(WebLinks.safeWebLinkOrNull("data:text/html,hello"))
    }
}
