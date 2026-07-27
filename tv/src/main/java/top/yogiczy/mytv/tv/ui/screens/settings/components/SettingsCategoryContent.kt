package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsCategories

@Composable
fun SettingsCategoryContent(
    modifier: Modifier = Modifier,
    currentCategoryProvider: () -> SettingsCategories = { SettingsCategories.entries.first() },
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
) {
    val currentCategory = currentCategoryProvider()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentCategory == SettingsCategories.ACCOUNT) {
                Image(
                    painter = painterResource(R.drawable.aulama_id_logo),
                    contentDescription = "Aulama ID",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(166.dp)
                        .height(35.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                            shape = RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = currentCategory.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = currentCategory.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.42f)),
        )

        val contentModifier = Modifier
            .weight(1f)
            .fillMaxWidth()

        when (currentCategory) {
            SettingsCategories.ABOUT -> SettingsCategoryAbout(contentModifier)
            SettingsCategories.ACCOUNT -> SettingsCategoryAccount(contentModifier)
            SettingsCategories.APP -> SettingsCategoryApp(contentModifier)
            SettingsCategories.IPTV -> SettingsCategoryIptv(
                modifier = contentModifier,
                channelGroupListProvider = channelGroupListProvider,
            )

            SettingsCategories.EPG -> SettingsCategoryEpg(contentModifier)
            SettingsCategories.EPG_RESERVE -> SettingsCategoryEpgReserve(contentModifier)
            SettingsCategories.UI -> SettingsCategoryUI(contentModifier)
            SettingsCategories.FAVORITE -> SettingsCategoryFavorite(contentModifier)
            SettingsCategories.UPDATE -> SettingsCategoryUpdate(contentModifier)
            SettingsCategories.VIDEO_PLAYER -> SettingsCategoryVideoPlayer(contentModifier)
            SettingsCategories.HTTP -> SettingsCategoryHttp(contentModifier)
            SettingsCategories.DEBUG -> SettingsCategoryDebug(contentModifier)
            SettingsCategories.LOG -> SettingsCategoryLog(contentModifier)
            SettingsCategories.MORE -> SettingsCategoryPush(contentModifier)
        }
    }
}
