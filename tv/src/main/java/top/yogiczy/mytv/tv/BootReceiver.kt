package top.yogiczy.mytv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.utils.Configs

/**
 * 開機自啓動監聽
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val sp = SP.getInstance(context)
            val bootLaunch = sp.getBoolean(Configs.KEY.APP_BOOT_LAUNCH.name, false)

            if (bootLaunch) {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}
