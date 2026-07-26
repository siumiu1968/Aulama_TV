package top.yogiczy.mytv.tv.account

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
        if (supportsPersistentRefreshToken(Build.VERSION.SDK_INT)) {
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
private class AndroidKeystoreRefreshTokenStore(
    context: Context,
) : RefreshTokenStore {
    private val tokenFile = File(context.noBackupFilesDir, TOKEN_FILE_NAME)

    override fun load(): RefreshTokenLoadResult {
        if (!tokenFile.isFile) return RefreshTokenLoadResult.Missing
        if (tokenFile.length() !in 1..MAX_TOKEN_FILE_BYTES) {
            clear()
            return RefreshTokenLoadResult.Invalidated
        }

        return runCatching {
            val envelope = EncryptedTokenEnvelopeCodec.decode(tokenFile.readText())
                ?: error("Invalid encrypted token envelope")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadKey() ?: error("Missing AndroidKeyStore key"),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv),
            )
            cipher.updateAAD(AUTHENTICATED_DATA)
            val plaintext = cipher.doFinal(envelope.ciphertext)
            val token = String(plaintext, StandardCharsets.UTF_8)
            plaintext.fill(0)
            require(token.isNotBlank())
            RefreshTokenLoadResult.Available(token)
        }.getOrElse {
            clear()
            RefreshTokenLoadResult.Invalidated
        }
    }

    override fun save(token: String): Result<Unit> = runCatching {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadKey() ?: createKey())
        cipher.updateAAD(AUTHENTICATED_DATA)
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
    }.onFailure { clear() }

    override fun clear() {
        runCatching { tokenFile.delete() }
        runCatching {
            keyStore().takeIf { it.containsAlias(KEY_ALIAS) }?.deleteEntry(KEY_ALIAS)
        }
    }

    private fun loadKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun writeAtomically(value: String) {
        tokenFile.parentFile?.mkdirs()
        val tempFile = File(tokenFile.parentFile, "$TOKEN_FILE_NAME.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(value.toByteArray(StandardCharsets.US_ASCII))
            output.fd.sync()
        }
        if (tokenFile.exists() && !tokenFile.delete()) {
            tempFile.delete()
            error("Unable to replace encrypted token")
        }
        if (!tempFile.renameTo(tokenFile)) {
            tempFile.delete()
            error("Unable to persist encrypted token")
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "aulama_iptv_refresh_token_v1"
        const val TOKEN_FILE_NAME = "aulama_refresh_token_v1.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val MAX_TOKEN_FILE_BYTES = 64 * 1024L
        val AUTHENTICATED_DATA = "aulama-iptv-refresh-token-v1"
            .toByteArray(StandardCharsets.US_ASCII)
    }
}
