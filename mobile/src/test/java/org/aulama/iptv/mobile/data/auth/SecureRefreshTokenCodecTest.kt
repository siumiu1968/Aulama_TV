package org.aulama.iptv.mobile.data.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureRefreshTokenCodecTest {
    @Test
    fun envelopeRoundTripsWithoutPlaintextToken() {
        val envelope = EncryptedTokenEnvelope(
            iv = byteArrayOf(1, 2, 3, 4),
            ciphertext = byteArrayOf(9, 8, 7, 6),
        )
        val encoded = EncryptedTokenEnvelopeCodec.encode(envelope)
        val decoded = EncryptedTokenEnvelopeCodec.decode(encoded)!!

        assertArrayEquals(envelope.iv, decoded.iv)
        assertArrayEquals(envelope.ciphertext, decoded.ciphertext)
        assertEquals(false, encoded.contains("refresh-token"))
    }

    @Test
    fun malformedEnvelopeIsRejected() {
        assertNull(EncryptedTokenEnvelopeCodec.decode("v1:not-hex:00"))
    }
}
