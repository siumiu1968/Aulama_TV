package org.aulama.iptv.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aulama.iptv.mobile.BuildConfig
import org.aulama.iptv.mobile.SyncUiState
import org.aulama.iptv.mobile.data.auth.AulamaAccountState
import org.aulama.iptv.mobile.ui.components.AuroraScaffold
import org.aulama.iptv.mobile.ui.components.AuroraTopBar
import org.aulama.iptv.mobile.ui.components.GlassPane

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    accountState: AulamaAccountState,
    syncState: SyncUiState,
    onToggleTheme: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
) {
    AuroraScaffold(
        topBar = { AuroraTopBar(title = "設定", onBack = onBack) },
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
                    .widthIn(max = 960.dp)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                emphasis = true,
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            AccountSettings(
                                accountState = accountState,
                                syncState = syncState,
                                onOpenAccount = onOpenAccount,
                                onOpenScanner = onOpenScanner,
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier.heightIn(min = 260.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        )
                        Column(Modifier.weight(1f)) {
                            AppSettings(
                                darkTheme = darkTheme,
                                onToggleTheme = onToggleTheme,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        AccountSettings(
                            accountState = accountState,
                            syncState = syncState,
                            onOpenAccount = onOpenAccount,
                            onOpenScanner = onOpenScanner,
                        )
                        SectionDivider()
                        AppSettings(
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSettings(
    accountState: AulamaAccountState,
    syncState: SyncUiState,
    onOpenAccount: () -> Unit,
    onOpenScanner: () -> Unit,
) {
    SectionLabel("帳戶與同步")
    SettingsRow(
        icon = Icons.Rounded.AccountCircle,
        title = "Aulama ID",
        subtitle = accountSubtitle(accountState, syncState),
        onClick = onOpenAccount,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    SettingsRow(
        icon = Icons.Rounded.QrCodeScanner,
        title = "掃描電視配對碼",
        subtitle = "掃描 QR code 或手動輸入配對碼",
        onClick = onOpenScanner,
    )
}

@Composable
private fun AppSettings(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val context = LocalContext.current
    val installedVersion = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: BuildConfig.VERSION_NAME
    }

    SectionLabel("顯示與關於")
    SettingsRow(
        icon = Icons.Rounded.DarkMode,
        title = "深色模式",
        subtitle = if (darkTheme) "已開啟" else "已關閉",
        onClick = onToggleTheme,
        trailing = {
            Switch(
                checked = darkTheme,
                onCheckedChange = { onToggleTheme() },
            )
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    SettingsRow(
        icon = Icons.Rounded.Info,
        title = "Aulama TV",
        subtitle = "版本 $installedVersion",
        onClick = null,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
    )
}

private fun accountSubtitle(
    accountState: AulamaAccountState,
    syncState: SyncUiState,
): String = when (accountState) {
    is AulamaAccountState.SignedIn -> when (syncState) {
        SyncUiState.Syncing -> "${accountState.profile.primaryLabel} · 同步中"
        is SyncUiState.Synced -> "${accountState.profile.primaryLabel} · 已同步"
        is SyncUiState.Unavailable -> "${accountState.profile.primaryLabel} · ${syncState.message}"
        SyncUiState.Idle -> accountState.profile.primaryLabel
    }
    AulamaAccountState.Restoring -> "正在恢復登入"
    is AulamaAccountState.SigningIn -> "正在登入"
    is AulamaAccountState.Unavailable -> accountState.message
    is AulamaAccountState.Guest -> accountState.notice ?: "未登入 · 訪客模式"
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (onClick == null) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
