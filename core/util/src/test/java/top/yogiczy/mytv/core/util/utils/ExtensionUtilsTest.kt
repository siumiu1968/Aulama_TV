package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionUtilsTest {
    @Test
    fun compareVersionHandlesReleaseSuffixes() {
        assertEquals(1, "2.6.11-family".compareVersion("2.6.10-family"))
        assertEquals(-1, "2.6.10-family".compareVersion("2.6.11-family"))
    }

    @Test
    fun compareVersionIgnoresMalformedVersionWithoutThrowing() {
        assertEquals(0, "Android".compareVersion("2.6.10-family"))
        assertEquals(0, "2.6.10-family".compareVersion("Android"))
    }
}
