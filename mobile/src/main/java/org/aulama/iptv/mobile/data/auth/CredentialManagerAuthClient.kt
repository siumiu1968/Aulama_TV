package org.aulama.iptv.mobile.data.auth

import android.app.Activity
import android.os.Build
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

sealed interface CredentialAttempt {
    data class GoogleIdentity(
        val idToken: String,
        val displayName: String?,
    ) : CredentialAttempt

    data class PasskeyAssertion(val responseJson: String) : CredentialAttempt

    data object Cancelled : CredentialAttempt

    data class Unavailable(val reason: String) : CredentialAttempt

    data class Failed(val reason: String) : CredentialAttempt
}

class CredentialManagerAuthClient(
    private val activity: Activity,
) {
    private val credentialManager = CredentialManager.create(activity)

    suspend fun requestGoogleIdentity(
        serverClientId: String,
        serverNonce: String,
    ): CredentialAttempt {
        if (serverClientId.isBlank()) {
            return CredentialAttempt.Unavailable("未設定 Google OAuth Web Client ID")
        }
        if (serverNonce.isBlank()) {
            return CredentialAttempt.Unavailable("Aulama ID 伺服器未有提供 Google nonce")
        }

        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(serverNonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return runCredentialRequest(request) { credential ->
            if (
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return@runCredentialRequest CredentialAttempt.Failed("登入供應商回傳咗不支援嘅憑證")
            }

            val googleCredential = runCatching {
                GoogleIdTokenCredential.createFrom(credential.data)
            }.getOrElse {
                return@runCredentialRequest CredentialAttempt.Failed("未能讀取 Google 登入結果")
            }

            if (googleCredential.idToken.isBlank()) {
                CredentialAttempt.Failed("Google 登入結果缺少身分憑證")
            } else {
                CredentialAttempt.GoogleIdentity(
                    idToken = googleCredential.idToken,
                    displayName = googleCredential.displayName,
                )
            }
        }
    }

    suspend fun requestPasskey(requestJson: String): CredentialAttempt {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return CredentialAttempt.Unavailable("此裝置需要 Android 9 或以上先可以使用 Passkey")
        }
        if (requestJson.isBlank()) {
            return CredentialAttempt.Unavailable("Aulama ID 伺服器尚未提供 Passkey challenge")
        }

        val request = runCatching {
            GetCredentialRequest.Builder()
                .addCredentialOption(GetPublicKeyCredentialOption(requestJson))
                .build()
        }.getOrElse {
            return CredentialAttempt.Failed("Passkey challenge 格式無效")
        }

        return runCredentialRequest(request) { credential ->
            if (credential !is PublicKeyCredential) {
                return@runCredentialRequest CredentialAttempt.Failed("登入供應商回傳咗不支援嘅憑證")
            }
            if (credential.authenticationResponseJson.isBlank()) {
                CredentialAttempt.Failed("Passkey 回應內容不完整")
            } else {
                CredentialAttempt.PasskeyAssertion(credential.authenticationResponseJson)
            }
        }
    }

    private suspend fun runCredentialRequest(
        request: GetCredentialRequest,
        onCredential: (androidx.credentials.Credential) -> CredentialAttempt,
    ): CredentialAttempt {
        return withTimeoutOrNull(CREDENTIAL_REQUEST_TIMEOUT_MS) {
            try {
                onCredential(
                    credentialManager.getCredential(
                        context = activity,
                        request = request,
                    ).credential
                )
            } catch (_: GetCredentialCancellationException) {
                CredentialAttempt.Cancelled
            } catch (_: NoCredentialException) {
                CredentialAttempt.Unavailable("裝置上暫時冇可用嘅登入憑證")
            } catch (error: GetCredentialException) {
                CredentialAttempt.Failed(error.message ?: "原生登入暫時未能使用")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                CredentialAttempt.Failed(error.message ?: "原生登入暫時未能使用")
            }
        } ?: CredentialAttempt.Failed("系統登入等候逾時，請重試")
    }

    private companion object {
        const val CREDENTIAL_REQUEST_TIMEOUT_MS = 45_000L
    }
}
