package org.aulama.iptv.mobile.data.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface RefreshTokenLoadResult {
    data object Missing : RefreshTokenLoadResult
    data class Available(val token: String) : RefreshTokenLoadResult
    data object Invalidated : RefreshTokenLoadResult
}

internal interface RefreshTokenStore {
    fun load(): RefreshTokenLoadResult
    fun save(token: String): Result<Unit>
    fun clear()
}

internal object RefreshTokenStoreFactory {
    fun create(context: Context): RefreshTokenStore =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AndroidKeystoreRefreshTokenStore(context.applicationContext)
        } else {
            MemoryRefreshTokenStore()
        }
}

internal class MemoryRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun load(): RefreshTokenLoadResult = token
        ?.let(RefreshTokenLoadResult::Available)
        ?: RefreshTokenLoadResult.Missing

    override fun save(token: String): Result<Unit> = runCatching {
        require(token.isNotBlank())
        this.token = token
    }

    override fun clear() {
        token = null
    }
}

@RequiresApi(Build.VERSION_CODES.M)
private class AndroidKeystoreRefreshTokenStore(context: Context) : RefreshTokenStore {
    private val tokenFile = File(context.noBackupFilesDir, TOKEN_FILE_NAME)

    override fun load(): RefreshTokenLoadResult {
        if (!tokenFile.isFile) return RefreshTokenLoadResult.Missing
        if (tokenFile.length() !in 1..MAX_FILE_BYTES) {
            clear()
            return RefreshTokenLoadResult.Invalidated
        }
        return runCatching {
            val envelope = EncryptedTokenEnvelopeCodec.decode(tokenFile.readText())
                ?: error("Invalid token envelope")
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    loadKey() ?: error("Missing AndroidKeyStore key"),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv),
                )
                updateAAD(AUTHENTICATED_DATA)
            }
            val plaintext = cipher.doFinal(envelope.ciphertext)
            val token = try {
                String(plaintext, StandardCharsets.UTF_8)
            } finally {
                plaintext.fill(0)
            }
            require(token.isNotBlank())
            RefreshTokenLoadResult.Available(token)
        }.getOrElse {
            clear()
            RefreshTokenLoadResult.Invalidated
        }
    }

    override fun save(token: String): Result<Unit> = runCatching {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, loadKey() ?: createKey())
            updateAAD(AUTHENTICATED_DATA)
        }
        val plaintext = token.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        writeAtomically(
            EncryptedTokenEnvelopeCodec.encode(
                EncryptedTokenEnvelope(iv = cipher.iv, ciphertext = ciphertext)
            )
        )
    }

    override fun clear() {
        tokenFile.delete()
        runCatching {
            androidKeyStore().deleteEntry(KEY_ALIAS)
        }
    }

    private fun loadKey(): SecretKey? = androidKeyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun writeAtomically(value: String) {
        tokenFile.parentFile?.mkdirs()
        val temporary = File(tokenFile.parentFile, "$TOKEN_FILE_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(tokenFile)) {
            temporary.delete()
            error("Unable to replace encrypted token")
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aulama_mobile_refresh_v1"
        const val TOKEN_FILE_NAME = "aulama_mobile_refresh.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val MAX_FILE_BYTES = 64 * 1024L
        val AUTHENTICATED_DATA = "org.aulama.iptv.mobile:refresh:v1".toByteArray()
    }
}

internal data class EncryptedTokenEnvelope(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal object EncryptedTokenEnvelopeCodec {
    fun encode(envelope: EncryptedTokenEnvelope): String {
        require(envelope.iv.isNotEmpty() && envelope.ciphertext.isNotEmpty())
        return "v1:${envelope.iv.toHex()}:${envelope.ciphertext.toHex()}"
    }

    fun decode(value: String): EncryptedTokenEnvelope? {
        val parts = value.trim().split(':')
        if (parts.size != 3 || parts[0] != "v1") return null
        val iv = parts[1].hexToBytesOrNull() ?: return null
        val ciphertext = parts[2].hexToBytesOrNull() ?: return null
        if (iv.isEmpty() || ciphertext.isEmpty()) return null
        return EncryptedTokenEnvelope(iv, ciphertext)
    }
}

private fun ByteArray.toHex(): String = joinToString("") {
    (it.toInt() and 0xff).toString(16).padStart(2, '0')
}

private fun String.hexToBytesOrNull(): ByteArray? {
    if (isEmpty() || length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
