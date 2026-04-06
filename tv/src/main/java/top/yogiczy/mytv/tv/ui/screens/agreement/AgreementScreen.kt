package top.yogiczy.mytv.tv.ui.screens.agreement

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.customBackground
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun AgreementScreen(
    modifier: Modifier = Modifier,
    onAgree: () -> Unit = {},
    onDisagree: () -> Unit = {},
    onDisableUiFocusOptimize: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .customBackground()
            .padding(top = childPadding.top),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("使用須知", style = MaterialTheme.typography.headlineMedium)

        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge
        ) {
            LazyColumn(
                modifier = Modifier.width(556.dp),
                contentPadding = PaddingValues(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val messages = listOf(
                    "歡迎使用${Constants.APP_TITLE}，請在使用前仔細閲讀以下內容：",
                    "1. 本軟件為開源軟件，僅供學習交流使用，禁止用於任何商業用途。",
                    "2. 本軟件不提供任何直播內容，所有直播內容均來自網絡。",
                    "3. 本軟件完全基於您個人意願使用，您應該對自己的使用行為和所有結果承擔全部責任。",
                    "4. 如果本軟件存在侵犯您的合法權益的情況，請及時與作者聯繫，作者將會及時刪除有關內容。",
                    "如您繼續使用本軟件即代表您已完全理解並同意上述內容。",
                )

                items(messages) { Text(it) }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = childPadding.bottom),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            modifier = Modifier
                                .focusOnLaunched()
                                .handleKeyEvents(onSelect = onAgree)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        onDisableUiFocusOptimize()
                                        onAgree()
                                    })
                                },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            ),
                            onClick = { },
                        ) {
                            Text("已閲讀並同意")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            modifier = Modifier
                                .handleKeyEvents(onSelect = onDisagree),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            ),
                            onClick = { },
                        ) {
                            Text("退出應用")
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AgreementScreenPreview() {
    MyTVTheme {
        AgreementScreen()
        PreviewWithLayoutGrids { }
    }
}