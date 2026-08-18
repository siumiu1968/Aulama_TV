package top.yogiczy.mytv.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.decode.SvgDecoder
import coil.memory.MemoryCache
import coil.request.CachePolicy
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.util.utils.ApkUpdateCache
import top.yogiczy.mytv.tv.account.AulamaAccount

class MyTVApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        AppData.init(applicationContext)
        ApkUpdateCache.cleanupAfterAppStart(applicationContext)
        AulamaAccount.initialize(applicationContext)
        UnsafeTrustManager.enableUnsafeTrustManager()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(OkHttp.client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
