package top.yogiczy.mytv.tv.account

internal const val ANDROID_KEYSTORE_MIN_API = 23

internal fun supportsPersistentRefreshToken(apiLevel: Int): Boolean =
    apiLevel >= ANDROID_KEYSTORE_MIN_API

internal data class EncryptedTokenEnvelope(
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is EncryptedTokenEnvelope &&
        iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

internal object EncryptedTokenEnvelopeCodec {
    private const val VERSION = "v1"

    fun encode(envelope: EncryptedTokenEnvelope): String {
        require(envelope.iv.isNotEmpty())
        require(envelope.ciphertext.isNotEmpty())
        return listOf(VERSION, envelope.iv.toHex(), envelope.ciphertext.toHex()).joinToString(":")
    }

    fun decode(value: String): EncryptedTokenEnvelope? {
        val parts = value.trim().split(':')
        if (parts.size != 3 || parts[0] != VERSION) return null
        val iv = parts[1].hexToBytesOrNull() ?: return null
        val ciphertext = parts[2].hexToBytesOrNull() ?: return null
        if (iv.isEmpty() || ciphertext.isEmpty()) return null
        return EncryptedTokenEnvelope(iv = iv, ciphertext = ciphertext)
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}

private fun String.hexToBytesOrNull(): ByteArray? {
    if (isEmpty() || length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
