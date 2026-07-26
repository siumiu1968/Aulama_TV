package org.aulama.iptv.mobile.ui.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodeParserTest {
    @Test
    fun acceptsOnlyExpectedAulamaPairingUrl() {
        assertEquals(
            PairingCode("ABCD-EF12"),
            PairingCodeParser.fromQr("https://aulama.org/iptv/pair/?code=abcd-ef12"),
        )
        assertEquals(
            PairingCode("ABCD-EF12"),
            PairingCodeParser.fromQr("https://aulama.org/iptv/pair?code=ABCD%2DEF12"),
        )
    }

    @Test
    fun rejectsUntrustedOrMalformedQrLinks() {
        assertNull(PairingCodeParser.fromQr("http://aulama.org/iptv/pair/?code=ABCD-EF12"))
        assertNull(PairingCodeParser.fromQr("https://aulama.org.evil/iptv/pair/?code=ABCD-EF12"))
        assertNull(PairingCodeParser.fromQr("https://aulama.org/login/?code=ABCD-EF12"))
        assertNull(PairingCodeParser.fromQr("https://aulama.org/iptv/pair/?code=short"))
        assertNull(PairingCodeParser.fromQr("https://aulama.org/iptv/pair/?code=%ZZ"))
        assertNull(PairingCodeParser.fromQr("ABCD-EF12"))
    }

    @Test
    fun manualEntryNormalizesSpacingAndHyphen() {
        assertEquals(
            PairingCode("ABCD-EF12"),
            PairingCodeParser.fromManual(" abcd ef12 "),
        )
        assertNull(PairingCodeParser.fromManual("ABCD-EF1!"))
    }
}
