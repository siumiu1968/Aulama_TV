package top.yogiczy.mytv.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import top.yogiczy.mytv.core.util.utils.ApkUpdateCache

/** 安裝新版本完成後，待系統已讀完 APK 才清理更新檔。 */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ApkUpdateCache.clearAll(context.cacheDir)
        }
    }
}
