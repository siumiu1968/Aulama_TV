package org.aulama.iptv.mobile.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.aulama.iptv.mobile.R
import org.aulama.iptv.mobile.ui.components.AuroraBackdrop
import org.aulama.iptv.mobile.ui.components.GlassPane

@Composable
fun WelcomeScreen(
    onContinueAsGuest: () -> Unit,
    onSignIn: () -> Unit,
) {
    AuroraBackdrop {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WelcomeBranding(
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    WelcomeActions(
                        onContinueAsGuest = onContinueAsGuest,
                        onSignIn = onSignIn,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 560.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WelcomeBranding(compact = false)
                    Spacer(Modifier.height(24.dp))
                    WelcomeActions(
                        onContinueAsGuest = onContinueAsGuest,
                        onSignIn = onSignIn,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeBranding(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.aulama_tv_logo),
            contentDescription = "Aulama TV",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.72f else 0.62f)
                .widthIn(max = 260.dp)
                .height(if (compact) 96.dp else 88.dp),
        )
        Spacer(Modifier.height(if (compact) 20.dp else 16.dp))
        Text(
            text = "歡迎使用 Aulama TV",
            style = if (compact) MaterialTheme.typography.headlineLarge
            else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "即開即睇，唔登入都可以使用。Aulama ID 會同步收藏、自訂頻道同線路優先。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun WelcomeActions(
    onContinueAsGuest: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPane(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        emphasis = true,
    ) {
        Column {
            WelcomeFeature(
                icon = Icons.Rounded.PlayCircle,
                title = "訪客模式",
                detail = "直接睇直播，唔需要建立帳戶。",
            )
            WelcomeFeature(
                icon = Icons.Rounded.Favorite,
                title = "Aulama ID",
                detail = "安全同步收藏、自訂 M3U 同線路優先。",
            )
            WelcomeFeature(
                icon = Icons.Rounded.LiveTv,
                title = "電視配對",
                detail = "喺設定掃描電視顯示嘅配對碼。",
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "以訪客身份繼續",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text("登入 Aulama ID")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "你可以之後喺設定登入或掃描電視配對碼。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WelcomeFeature(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(11.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
