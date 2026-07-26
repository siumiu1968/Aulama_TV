package top.yogiczy.mytv.tv.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureStoreHelpersTest {
    @Test
    fun `persistent token storage starts at api 23`() {
        assertFalse(supportsPersistentRefreshToken(21))
        assertFalse(supportsPersistentRefreshToken(22))
        assertTrue(supportsPersistentRefreshToken(23))
        assertTrue(supportsPersistentRefreshToken(35))
    }

    @Test
    fun `encrypted envelope round trips without plaintext`() {
        val original = EncryptedTokenEnvelope(
            iv = byteArrayOf(0, 1, 2, 127, -1),
            ciphertext = byteArrayOf(9, 8, 7, 6, 5),
        )

        val encoded = EncryptedTokenEnvelopeCodec.encode(original)
        val decoded = EncryptedTokenEnvelopeCodec.decode(encoded)

        assertFalse(encoded.contains("refresh-token"))
        assertArrayEquals(original.iv, decoded?.iv)
        assertArrayEquals(original.ciphertext, decoded?.ciphertext)
    }

    @Test
    fun `encrypted envelope rejects wrong version and malformed hex`() {
        assertNull(EncryptedTokenEnvelopeCodec.decode("v2:00:11"))
        assertNull(EncryptedTokenEnvelopeCodec.decode("v1:0:11"))
        assertNull(EncryptedTokenEnvelopeCodec.decode("v1:zz:11"))
        assertNull(EncryptedTokenEnvelopeCodec.decode("v1::11"))
    }

    @Test
    fun `memory store clears token without persistence`() {
        val store = MemoryRefreshTokenStore()
        assertEquals(RefreshTokenLoadResult.Missing, store.load())

        assertTrue(store.save("refresh-token").isSuccess)
        assertEquals(
            RefreshTokenLoadResult.Available("refresh-token"),
            store.load(),
        )

        store.clear()
        assertEquals(RefreshTokenLoadResult.Missing, store.load())
    }
}
