package top.yogiczy.mytv.tv.ui.screens.account

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import top.yogiczy.mytv.tv.account.AulamaAccount
import top.yogiczy.mytv.tv.account.AulamaAccountManager
import top.yogiczy.mytv.tv.account.AulamaAccountState
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screens.components.Qrcode
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.customBackground
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
internal fun DevicePairingScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    accountManager: AulamaAccountManager = AulamaAccount.manager,
) {
    val state by accountManager.state.collectAsState()
    var pairingRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (!pairingRequested && state is AulamaAccountState.Guest) {
            pairingRequested = true
            accountManager.startPairing()
        }
    }

    fun closeAsGuest() {
        if (state !is AulamaAccountState.SignedIn) {
            accountManager.continueAsGuest("已略過登入，可隨時到設定重新配對")
        }
        onClose()
    }

    val childPadding = rememberChildPadding()
    Box(
        modifier = modifier
            .fillMaxSize()
            .captureBackKey(::closeAsGuest)
            .customBackground()
            .padding(
                start = childPadding.start,
                top = childPadding.top,
                end = childPadding.end,
                bottom = childPadding.bottom,
            ),
    ) {
        Column {
            Text(
                text = "Aulama ID",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "用手機或網頁完成安全配對；唔登入亦可繼續睇電視。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .sizeIn(maxWidth = 780.dp),
        ) {
            when (val current = state) {
                AulamaAccountState.Restoring -> PairingLoading(
                    title = "正在恢復登入",
                    message = "訪客播放唔會受到影響。",
                    onGuest = ::closeAsGuest,
                )

                AulamaAccountState.StartingPairing -> PairingLoading(
                    title = "正在建立配對碼",
                    message = "請稍候，期間可以選擇以訪客使用。",
                    onGuest = ::closeAsGuest,
                )

                is AulamaAccountState.Pairing -> PairingCodeContent(
                    state = current,
                    onGuest = ::closeAsGuest,
                )

                is AulamaAccountState.SignedIn -> SignedInContent(
                    state = current,
                    onDone = onClose,
                    onLogout = {
                        accountManager.logout()
                        onClose()
                    },
                )

                is AulamaAccountState.Unavailable -> PairingUnavailable(
                    title = when (current.kind) {
                        top.yogiczy.mytv.tv.account.UnavailableKind.CONFIGURATION ->
                            "配對服務尚未配置"

                        top.yogiczy.mytv.tv.account.UnavailableKind.NETWORK ->
                            "暫時連接唔到服務"

                        top.yogiczy.mytv.tv.account.UnavailableKind.SECURE_STORAGE ->
                            "無法安全保存登入"

                        top.yogiczy.mytv.tv.account.UnavailableKind.SESSION ->
                            "配對未完成"
                    },
                    message = current.message,
                    onRetry = accountManager::retry,
                    onGuest = ::closeAsGuest,
                )

                AulamaAccountState.Expired -> PairingUnavailable(
                    title = "配對碼已過期",
                    message = "請重新產生一個配對碼，舊碼已經無法使用。",
                    onRetry = accountManager::startPairing,
                    onGuest = ::closeAsGuest,
                )

                is AulamaAccountState.Guest -> PairingUnavailable(
                    title = "以訪客使用",
                    message = current.notice ?: "登入係自選功能，你可以稍後到設定再配對。",
                    onRetry = accountManager::startPairing,
                    onGuest = onClose,
                )
            }
        }
    }
}

@Composable
private fun PairingCodeContent(
    state: AulamaAccountState.Pairing,
    onGuest: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Qrcode(
            modifier = Modifier.size(224.dp),
            textProvider = { state.verificationUriComplete },
        )

        Column(
            modifier = Modifier.width(450.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("掃描 QR code 完成登入", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "配對碼  ${state.userCode}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "QR code 只包含一次性配對網址。手機或網頁會要求你登入並確認呢部電視。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            Text(
                text = if (state.networkRetry) {
                    "網絡暫時中斷，系統會按原有間隔自動重試"
                } else {
                    "等待手機或網頁確認中"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.networkRetry) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            PairingRemainingTime(state.expiresAtMs)
            Spacer(Modifier.height(2.dp))
            PairingButton(
                text = "取消並以訪客使用",
                onSelected = onGuest,
                focusOnLaunch = true,
            )
        }
    }
}

@Composable
private fun PairingRemainingTime(expiresAtMs: Long) {
    var nowMs by remember(expiresAtMs) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(expiresAtMs) {
        while (nowMs < expiresAtMs) {
            delay(1_000L)
            nowMs = SystemClock.elapsedRealtime()
        }
    }
    val seconds = ((expiresAtMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L
    Text(
        text = "有效時間：${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
    )
}

@Composable
private fun PairingLoading(
    title: String,
    message: String,
    onGuest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(42.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        PairingButton(
            text = "以訪客使用",
            onSelected = onGuest,
            focusOnLaunch = true,
        )
    }
}

@Composable
private fun PairingUnavailable(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            textAlign = TextAlign.Center,
            modifier = Modifier.sizeIn(maxWidth = 620.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PairingButton("重試", onRetry, focusOnLaunch = true)
            PairingButton("以訪客使用", onGuest)
        }
    }
}

@Composable
private fun SignedInContent(
    state: AulamaAccountState.SignedIn,
    onDone: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("配對完成", style = MaterialTheme.typography.headlineSmall)
        Text(state.profile.primaryLabel, style = MaterialTheme.typography.headlineMedium)
        state.profile.email?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "身份：${state.profile.roleLabel}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        state.connectionNotice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PairingButton("完成", onDone, focusOnLaunch = true)
            PairingButton("登出", onLogout)
        }
    }
}

@Composable
private fun PairingButton(
    text: String,
    onSelected: () -> Unit,
    focusOnLaunch: Boolean = false,
) {
    Button(
        modifier = Modifier
            .then(if (focusOnLaunch) Modifier.focusOnLaunched(text) else Modifier)
            .handleKeyEvents(onSelect = onSelected)
            .pointerInput(onSelected) {
                detectTapGestures(onTap = { onSelected() })
            },
        onClick = {},
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = ButtonDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            )
        ),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ),
    ) {
        Text(text)
    }
}
