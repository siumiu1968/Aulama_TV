package org.aulama.iptv.mobile.ui.pairing

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
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.aulama.iptv.mobile.PairingApprovalState
import org.aulama.iptv.mobile.data.auth.AulamaAccountState
import org.aulama.iptv.mobile.ui.components.AuroraScaffold
import org.aulama.iptv.mobile.ui.components.AuroraTopBar
import org.aulama.iptv.mobile.ui.components.GlassPane

@Composable
fun PairingConfirmationScreen(
    pairingCode: PairingCode,
    accountState: AulamaAccountState,
    approvalState: PairingApprovalState,
    onApprove: () -> Unit,
    onSignIn: () -> Unit,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val signedIn = accountState is AulamaAccountState.SignedIn

    AuroraScaffold(
        topBar = { AuroraTopBar(title = "確認電視配對", onBack = onScanAgain) },
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
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                emphasis = true,
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PairingOverview(
                            pairingCode = pairingCode,
                            modifier = Modifier.weight(1f),
                        )
                        PairingActions(
                            signedIn = signedIn,
                            approvalState = approvalState,
                            onApprove = onApprove,
                            onSignIn = onSignIn,
                            onScanAgain = onScanAgain,
                            onDone = onDone,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        PairingOverview(pairingCode = pairingCode)
                        PairingActions(
                            signedIn = signedIn,
                            approvalState = approvalState,
                            onApprove = onApprove,
                            onSignIn = onSignIn,
                            onScanAgain = onScanAgain,
                            onDone = onDone,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingOverview(
    pairingCode: PairingCode,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(58.dp),
        )
        Text(
            text = "核對電視上嘅配對碼",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "只有當電視顯示相同配對碼，而且係你本人發起，先好繼續。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = pairingCode.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            )
        }
    }
}

@Composable
private fun PairingActions(
    signedIn: Boolean,
    approvalState: PairingApprovalState,
    onApprove: () -> Unit,
    onSignIn: () -> Unit,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!signedIn) {
            StatusMessage(
                title = "請先登入 Aulama ID",
                detail = "配對批准需要登入帳戶；目前未有向電視發出任何授權。",
                error = true,
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("登入 Aulama ID")
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("返回設定")
            }
            return@Column
        }

        when (approvalState) {
            PairingApprovalState.Idle -> {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("確認配對")
                }
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("取消並重新掃描")
                }
            }

            PairingApprovalState.Approving -> {
                CircularProgressIndicator()
                Text("正在安全批准電視登入")
            }

            PairingApprovalState.Approved -> {
                StatusMessage(
                    title = "配對已批准",
                    detail = "電視會自動完成登入，毋須輸入帳戶密碼。",
                    error = false,
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("返回設定")
                }
            }

            is PairingApprovalState.Failed -> {
                StatusMessage(
                    title = if (approvalState.configurationMissing) "配置未完成" else "配對未完成",
                    detail = approvalState.message,
                    error = true,
                )
                Button(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("重新嘗試")
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(
    title: String,
    detail: String,
    error: Boolean,
) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.86f)
        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f),
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
