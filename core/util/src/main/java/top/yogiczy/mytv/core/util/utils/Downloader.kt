package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object Downloader {
    internal const val CONNECT_TIMEOUT_SECONDS = 30L
    internal const val READ_TIMEOUT_SECONDS = 120L
    internal const val WRITE_TIMEOUT_SECONDS = 30L

    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            val client = createClient(onProgressCb)
            val request = okhttp3.Request.Builder().url(url).build()
            val file = File(filePath)

            try {
                with(client.newCall(request).execute()) {
                    if (!isSuccessful) throw Exception("下載文件失敗: $code")

                    val responseBody = body ?: throw Exception("下載文件失敗: 回應內容為空")
                    file.parentFile?.mkdirs()
                    responseBody.byteStream().use { input ->
                        FileOutputStream(file).buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (ex: Exception) {
                file.delete()
                throw Exception("下載文件失敗，請檢查網絡連接", ex)
            }
        }

    internal fun createClient(onProgressCb: ((Int) -> Unit)?): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
        }

        return OkHttpClient.Builder()
            .addNetworkInterceptor(interceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(
                UnsafeTrustManager.getSSLSocketFactory(),
                UnsafeTrustManager()
            )
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        private var lastProgress = -1

        override fun contentLength() = originalResponse.body!!.contentLength()

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            return object : ForwardingSource(originalResponse.body!!.source()) {
                var totalBytesRead = 0L

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    val totalBytes = contentLength()
                    if (totalBytes > 0) {
                        val progress = (totalBytesRead * 100 / totalBytes)
                            .toInt()
                            .coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgressCb?.invoke(progress)
                        }
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}
