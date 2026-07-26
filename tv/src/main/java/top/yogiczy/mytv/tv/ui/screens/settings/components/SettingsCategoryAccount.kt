package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import top.yogiczy.mytv.tv.account.AulamaAccount
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
                    SettingsListItem(
                        modifier = Modifier.focusRequester(firstFocusRequester),
                        headlineContent = "帳戶",
                        supportingContent = current.profile.email,
                        trailingContent = current.profile.primaryLabel,
                    )
                }
                item {
                    SettingsListItem(
                        headlineContent = "帳戶身份",
                        supportingContent = if (current.profile.isSuperAdmin) {
                            "中轉線路資格仍會由伺服器逐次驗證"
                        } else {
                            "一般帳戶不會取得管理員中轉線路"
                        },
                        trailingContent = current.profile.roleLabel,
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
