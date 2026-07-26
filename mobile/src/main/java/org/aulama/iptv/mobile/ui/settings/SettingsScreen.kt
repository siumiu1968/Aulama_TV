package org.aulama.iptv.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aulama.iptv.mobile.BuildConfig
import org.aulama.iptv.mobile.SyncUiState
import org.aulama.iptv.mobile.data.auth.AulamaAccountState

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "帳戶",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.AccountCircle,
                title = "Aulama ID",
                subtitle = accountSubtitle(accountState, syncState),
                onClick = onOpenAccount,
            )
            SettingsRow(
                icon = Icons.Rounded.QrCodeScanner,
                title = "掃描電視配對碼",
                subtitle = "掃描 QR code 或手動輸入配對碼",
                onClick = onOpenScanner,
            )

            Text(
                text = "顯示",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
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

            Text(
                text = "關於",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "Aulama TV",
                subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                onClick = null,
            )
        }
    }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(25.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
