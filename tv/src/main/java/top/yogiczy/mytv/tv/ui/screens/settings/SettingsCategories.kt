package top.yogiczy.mytv.tv.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsCategories(
    val icon: ImageVector,
    val title: String
) {
    ABOUT(Icons.Default.Info, "關於"),
    APP(Icons.Default.Settings, "應用"),
    IPTV(Icons.Default.LiveTv, "直播源"),
    EPG(Icons.Default.Menu, "節目單"),
    EPG_RESERVE(Icons.Default.Bookmark, "預約"),
    UI(Icons.Default.DisplaySettings, "界面"),
    FAVORITE(Icons.Default.Star, "收藏"),
    UPDATE(Icons.Default.Update, "更新"),
    VIDEO_PLAYER(Icons.Default.SmartDisplay, "播放器"),
    HTTP(Icons.Default.Http, "網絡"),
    DEBUG(Icons.Default.BugReport, "調試"),
    LOG(Icons.Default.FormatListNumbered, "日誌"),
    MORE(Icons.Default.CloudUpload, "推送"),
}