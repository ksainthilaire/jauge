package io.jauge.feature.account

import io.jauge.feature.account.about.displayVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/** The About screen shows only the public version, never a build suffix. */
class AboutVersionTest {
    /** Plain versions are kept as they are. */
    @Test
    fun keepsPublicVersion() {
        assertEquals("1.0", displayVersion("1.0"))
        assertEquals("0.1.0", displayVersion("0.1.0"))
    }

    /** Build suffixes and internal numbers are removed. */
    @Test
    fun removesBuildSuffixes() {
        assertEquals("1.2.3", displayVersion("1.2.3-debug"))
        assertEquals("1.2", displayVersion("1.2+42"))
        assertEquals("2.0", displayVersion("2.0 (17)"))
    }

    /** A version without a public part is not shown. */
    @Test
    fun emptyWhenUnreadable() {
        assertEquals("", displayVersion(""))
        assertEquals("", displayVersion("internal"))
    }
}
