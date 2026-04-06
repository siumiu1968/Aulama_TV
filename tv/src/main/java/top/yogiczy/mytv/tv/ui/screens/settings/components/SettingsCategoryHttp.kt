package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.util.utils.humanizeMs

@Composable
fun SettingsCategoryHttp(
    modifier: Modifier = Modifier,
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "HTTP請求重試次數",
                supportingContent = "影響直播源、節目單數據獲取",
                trailingContent = Constants.HTTP_RETRY_COUNT.toString(),
                locK = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "HTTP請求重試間隔時間",
                supportingContent = "影響直播源、節目單數據獲取",
                trailingContent = Constants.HTTP_RETRY_INTERVAL.humanizeMs(),
                locK = true,
            )
        }
    }
}