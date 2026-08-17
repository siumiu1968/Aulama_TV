package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionUtilsTest {
    @Test
    fun compareVersionHandlesReleaseSuffixes() {
        assertEquals(1, "2.6.11-family".compareVersion("2.6.10-family"))
        assertEquals(-1, "2.6.10-family".compareVersion("2.6.11-family"))
        assertEquals(1, "2.6.19-beta.1".compareVersion("2.6.18-family"))
        assertTrue("2.6.19-family".compareVersion("2.6.19-beta.1") > 0)
        assertTrue("2.6.19-beta.10".compareVersion("2.6.19-beta.2") > 0)
    }

    @Test
    fun compareVersionIgnoresMalformedVersionWithoutThrowing() {
        assertEquals(0, "Android".compareVersion("2.6.10-family"))
        assertEquals(0, "2.6.10-family".compareVersion("Android"))
    }
}
