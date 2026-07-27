package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.account.AulamaAccount
import top.yogiczy.mytv.tv.account.AulamaAccountProfile
import top.yogiczy.mytv.tv.account.AulamaAccountState
import top.yogiczy.mytv.tv.account.AulamaSyncState
import top.yogiczy.mytv.tv.account.AulamaTvSync
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.account.DevicePairingScreen

@Composable
fun SettingsCategoryAccount(modifier: Modifier = Modifier) {
    val accountManager = AulamaAccount.manager
    val state by accountManager.state.collectAsState()
    val syncState by AulamaTvSync.state.collectAsState()
    val popupManager = LocalPopupManager.current
    val pairingFocusRequester = remember { FocusRequester() }
    var showPairing by remember { mutableStateOf(false) }

    fun openPairing() {
        if (state !is AulamaAccountState.Pairing &&
            state !is AulamaAccountState.StartingPairing &&
            state !is AulamaAccountState.Restoring
        ) {
            accountManager.startPairing()
        }
        popupManager.push(pairingFocusRequester, true)
        showPairing = true
    }

    SettingsContentList(modifier) { firstFocusRequester ->
        when (val current = state) {
            is AulamaAccountState.SignedIn -> {
                item {
                    SignedInIdentityPanel(
                        profile = current.profile,
                        syncState = syncState,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    )
                }
                current.connectionNotice?.let { notice ->
                    item {
                        SettingsListItem(
                            headlineContent = "連線狀態",
                            trailingContent = notice,
                        )
                    }
                }
                item {
                    SettingsListItem(
                        modifier = Modifier.focusRequester(firstFocusRequester),
                        headlineContent = "資料同步",
                        supportingContent = syncStatus(syncState),
                        trailingContent = if (syncState is AulamaSyncState.Syncing) "同步中" else "立即同步",
                        onSelected = {
                            AulamaTvSync.syncNow()
                            Snackbar.show("正在同步收藏、直播源及優先線路")
                        },
                    )
                }
                item {
                    SettingsListItem(
                        headlineContent = "登出 Aulama ID",
                        supportingContent = "只會清除呢部電視上的登入資料",
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "登出",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        onSelected = {
                            accountManager.logout()
                            Snackbar.show("已登出 Aulama ID")
                        },
                    )
                }
            }

            else -> {
                val status = accountStatus(current)
                item {
                    GuestIdentityPanel(
                        status = status.first,
                        supportingContent = status.second,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    )
                }
                item {
                    SettingsListItem(
                        modifier = Modifier.focusRequester(firstFocusRequester),
                        headlineContent = "登入狀態",
                        supportingContent = status.second,
                        trailingContent = status.first,
                    )
                }
                item {
                    SettingsListItem(
                        modifier = Modifier.focusRequester(pairingFocusRequester),
                        headlineContent = if (current is AulamaAccountState.Pairing) {
                            "繼續配對"
                        } else {
                            "登入 Aulama ID"
                        },
                        supportingContent = "用手機掃 QR code 或到網頁輸入配對碼",
                        onSelected = ::openPairing,
                    )
                }
            }
        }
    }

    SimplePopup(
        visibleProvider = { showPairing },
        onDismissRequest = {
            if (state !is AulamaAccountState.SignedIn) {
                accountManager.continueAsGuest("已取消配對")
            }
            showPairing = false
        },
    ) {
        DevicePairingScreen(onClose = { showPairing = false })
    }
}

private data class IdentityStyle(
    val accent: Color,
    val icon: ImageVector,
    val editionLabel: String,
    val verificationLabel: String,
    val statusLabel: String,
    val accessLabel: String,
)

@Composable
private fun SignedInIdentityPanel(
    profile: AulamaAccountProfile,
    syncState: AulamaSyncState,
    modifier: Modifier = Modifier,
) {
    val style = profile.identityStyle()
    IdentityPanel(
        modifier = modifier,
        name = profile.primaryLabel,
        secondaryLabel = profile.email ?: "Aulama ID 已驗證帳戶",
        roleLabel = profile.roleLabel,
        style = style,
        syncLabel = compactSyncStatus(syncState),
        footnote = if (profile.isSuperAdmin || profile.role == "super_admin") {
            "播放前由伺服器逐次驗證"
        } else {
            "使用標準直連線路"
        },
    )
}

@Composable
private fun GuestIdentityPanel(
    status: String,
    supportingContent: String?,
    modifier: Modifier = Modifier,
) {
    IdentityPanel(
        modifier = modifier,
        name = "訪客模式",
        secondaryLabel = supportingContent ?: "毋須登入都可以正常播放",
        roleLabel = status,
        style = IdentityStyle(
            accent = Color(0xFFAAB3C0),
            icon = Icons.Default.PersonOutline,
            editionLabel = "AULAMA ID · GUEST",
            verificationLabel = "未登入",
            statusLabel = "未驗證",
            accessLabel = "標準直連",
        ),
        syncLabel = "未同步",
        footnote = "登入後可以同步收藏、直播源及優先線路",
    )
}

@Composable
private fun IdentityPanel(
    name: String,
    secondaryLabel: String,
    roleLabel: String,
    style: IdentityStyle,
    syncLabel: String,
    footnote: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val warmWhite = Color(0xFFF2EFE8)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(158.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF171817),
                        Color(0xFF1C1B18),
                        style.accent.copy(alpha = 0.13f),
                    ),
                ),
                shape = shape,
            )
            .border(1.dp, style.accent.copy(alpha = 0.34f), shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 19.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = style.editionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = style.accent,
                )

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(style.accent, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = style.verificationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = warmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                CredentialSeal(style)
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.width(137.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = style.accent,
                        maxLines = 1,
                    )
                    Text(
                        text = style.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = style.accent.copy(alpha = 0.72f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(style.accent.copy(alpha = 0.14f)),
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = style.accent,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "${style.accessLabel}  ·  $footnote",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = warmWhite.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = syncLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = style.accent.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun CredentialSeal(style: IdentityStyle) {
    Box(
        modifier = Modifier
            .size(57.dp)
            .border(1.dp, style.accent.copy(alpha = 0.68f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .border(1.dp, style.accent.copy(alpha = 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = style.accent,
            )
        }
    }
}

private fun AulamaAccountProfile.identityStyle(): IdentityStyle = when {
    isSuperAdmin || role == "super_admin" -> IdentityStyle(
        accent = Color(0xFFE2C27A),
        icon = Icons.Default.Diamond,
        editionLabel = "AULAMA ID · SIGNATURE",
        verificationLabel = "身份已驗證",
        statusLabel = "最高權限",
        accessLabel = "香港・日本中轉",
    )

    role == "admin" -> IdentityStyle(
        accent = Color(0xFF84B8FF),
        icon = Icons.Default.AdminPanelSettings,
        editionLabel = "AULAMA ID · ADMIN",
        verificationLabel = "身份已驗證",
        statusLabel = "管理權限",
        accessLabel = "標準直連",
    )

    role == "premium" -> IdentityStyle(
        accent = Color(0xFFFF8CC8),
        icon = Icons.Default.WorkspacePremium,
        editionLabel = "AULAMA ID · PREMIUM",
        verificationLabel = "身份已驗證",
        statusLabel = "Premium 專屬",
        accessLabel = "標準直連",
    )

    else -> IdentityStyle(
        accent = Color(0xFF5DDCEB),
        icon = Icons.Default.VerifiedUser,
        editionLabel = "AULAMA ID · MEMBER",
        verificationLabel = "身份已驗證",
        statusLabel = "已驗證會員",
        accessLabel = "標準直連",
    )
}

private fun compactSyncStatus(state: AulamaSyncState): String = when (state) {
    AulamaSyncState.Idle -> "準備同步"
    AulamaSyncState.Syncing -> "同步中"
    is AulamaSyncState.Synced -> "版本 ${state.revision}"
    is AulamaSyncState.Deferred -> "稍後重試"
}

private fun syncStatus(state: AulamaSyncState): String = when (state) {
    AulamaSyncState.Idle -> "登入後會自動同步"
    AulamaSyncState.Syncing -> "正在保守合併其他裝置的變更"
    is AulamaSyncState.Synced -> "已同步至版本 ${state.revision}"
    is AulamaSyncState.Deferred -> state.message
}

private fun accountStatus(state: AulamaAccountState): Pair<String, String?> = when (state) {
    is AulamaAccountState.Guest -> "訪客" to state.notice
    AulamaAccountState.Restoring -> "恢復中" to "正在安全恢復登入；訪客播放不受影響"
    AulamaAccountState.StartingPairing -> "建立配對碼" to "正在連接 Aulama ID"
    is AulamaAccountState.Pairing -> "等待配對" to if (state.networkRetry) {
        "網絡暫時中斷，系統會自動重試"
    } else {
        "請在配對碼過期前完成確認"
    }

    is AulamaAccountState.Unavailable -> "未登入" to state.message
    AulamaAccountState.Expired -> "已過期" to "請重新產生配對碼"
    is AulamaAccountState.SignedIn -> "已登入" to null
}
