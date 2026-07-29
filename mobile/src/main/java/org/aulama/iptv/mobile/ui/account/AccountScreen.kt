package org.aulama.iptv.mobile.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aulama.iptv.mobile.data.auth.AccountUnavailableKind
import org.aulama.iptv.mobile.data.auth.AulamaAccountState
import org.aulama.iptv.mobile.data.auth.AuthCapabilityResolver
import org.aulama.iptv.mobile.data.auth.AuthCapabilityStatus
import org.aulama.iptv.mobile.data.auth.CredentialManagerAuthClient
import org.aulama.iptv.mobile.ui.components.AuroraScaffold
import org.aulama.iptv.mobile.ui.components.AuroraTopBar
import org.aulama.iptv.mobile.ui.components.GlassPane

@Composable
fun AccountScreen(
    accountState: AulamaAccountState,
    showGuestContinue: Boolean,
    onGoogleSignIn: (CredentialManagerAuthClient) -> Unit,
    onPasskeySignIn: (CredentialManagerAuthClient) -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authClient = remember(activity) { activity?.let(::CredentialManagerAuthClient) }
    val capabilities = remember {
        AuthCapabilityResolver.resolve(
            sdkInt = Build.VERSION.SDK_INT,
            googleWebClientId = "provided-by-aulama-backend",
            hasPasskeyChallenge = true,
        )
    }
    val loading = loadingState(accountState)

    AuroraScaffold(
        topBar = { AuroraTopBar(title = "Aulama ID", onBack = onBack) },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            GlassPane(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                emphasis = true,
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AccountOverview(
                            accountState = accountState,
                            modifier = Modifier.weight(1f),
                        )
                        AccountActions(
                            accountState = accountState,
                            showGuestContinue = showGuestContinue,
                            loading = loading,
                            authClient = authClient,
                            googleAvailable = capabilities.google == AuthCapabilityStatus.AVAILABLE,
                            passkeyStatus = capabilities.passkey,
                            onGoogleSignIn = onGoogleSignIn,
                            onPasskeySignIn = onPasskeySignIn,
                            onLogout = onLogout,
                            onSyncNow = onSyncNow,
                            onContinueAsGuest = onContinueAsGuest,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        AccountOverview(accountState = accountState)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        )
                        AccountActions(
                            accountState = accountState,
                            showGuestContinue = showGuestContinue,
                            loading = loading,
                            authClient = authClient,
                            googleAvailable = capabilities.google == AuthCapabilityStatus.AVAILABLE,
                            passkeyStatus = capabilities.passkey,
                            onGoogleSignIn = onGoogleSignIn,
                            onPasskeySignIn = onPasskeySignIn,
                            onLogout = onLogout,
                            onSyncNow = onSyncNow,
                            onContinueAsGuest = onContinueAsGuest,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountOverview(
    accountState: AulamaAccountState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountIdentityCard(accountState)
        Text(
            text = if (accountState is AulamaAccountState.SignedIn) {
                "收藏、自訂 M3U 及手動線路優先會透過 Aulama ID 同步；線路健康分數只留喺呢部裝置。"
            } else {
                "登入後可以跨裝置同步收藏及自訂頻道；唔登入亦可以繼續睇直播。"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountActions(
    accountState: AulamaAccountState,
    showGuestContinue: Boolean,
    loading: Boolean,
    authClient: CredentialManagerAuthClient?,
    googleAvailable: Boolean,
    passkeyStatus: AuthCapabilityStatus,
    onGoogleSignIn: (CredentialManagerAuthClient) -> Unit,
    onPasskeySignIn: (CredentialManagerAuthClient) -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (accountState is AulamaAccountState.SignedIn) {
            Text(
                text = "帳戶操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("立即同步")
            }
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("登出")
            }
        } else {
            Text(
                text = "登入方式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Button(
                onClick = { authClient?.let(onGoogleSignIn) },
                enabled = !loading && authClient != null && googleAvailable,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (accountState is AulamaAccountState.SigningIn &&
                    accountState.provider == "Google"
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                }
                Spacer(Modifier.width(10.dp))
                Text("使用 Google 繼續")
            }

            OutlinedButton(
                onClick = { authClient?.let(onPasskeySignIn) },
                enabled = !loading && authClient != null &&
                    passkeyStatus == AuthCapabilityStatus.AVAILABLE,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (accountState is AulamaAccountState.SigningIn &&
                    accountState.provider == "Passkey"
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                }
                Spacer(Modifier.width(10.dp))
                Text("使用 Passkey")
            }

            if (passkeyStatus == AuthCapabilityStatus.UNSUPPORTED) {
                ConfigurationHint(
                    title = "此裝置未支援 Passkey",
                    detail = "需要 Android 9 或以上；仍可使用 Google 或訪客模式。",
                )
            }

            Text(
                text = "Google 會使用裝置上嘅帳戶安全登入；Passkey 由系統驗證，密碼唔會交俾 Aulama TV。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showGuestContinue) {
                TextButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("暫不登入，以訪客身份繼續")
                }
            }

            if (accountState is AulamaAccountState.Unavailable) {
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清除登入資料")
                }
            }
        }
    }
}

@Composable
private fun AccountIdentityCard(state: AulamaAccountState) {
    val profile = (state as? AulamaAccountState.SignedIn)?.profile
    val isError = state is AulamaAccountState.Unavailable
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.84f)
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f),
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(50.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile?.primaryLabel ?: when (state) {
                        AulamaAccountState.Restoring -> "正在恢復登入"
                        is AulamaAccountState.SigningIn -> "正在使用 ${state.provider} 登入"
                        is AulamaAccountState.Unavailable -> when (state.kind) {
                            AccountUnavailableKind.CONFIGURATION -> "配置未完成"
                            AccountUnavailableKind.NETWORK -> "連線暫時中斷"
                            else -> "登入未完成"
                        }
                        else -> "訪客模式"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = profile?.roleLabel ?: when (state) {
                        is AulamaAccountState.Guest -> state.notice ?: "未登入 Aulama ID"
                        is AulamaAccountState.Unavailable -> state.message
                        else -> "請稍候"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                (state as? AulamaAccountState.SignedIn)?.connectionNotice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loadingState(state)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun ConfigurationHint(title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun loadingState(state: AulamaAccountState): Boolean =
    state is AulamaAccountState.Restoring || state is AulamaAccountState.SigningIn

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
