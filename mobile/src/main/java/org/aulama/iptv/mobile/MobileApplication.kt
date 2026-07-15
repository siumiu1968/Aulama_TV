package org.aulama.iptv.mobile

import android.app.Application
import top.yogiczy.mytv.core.data.AppData

class MobileApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppData.init(applicationContext)
    }
}
