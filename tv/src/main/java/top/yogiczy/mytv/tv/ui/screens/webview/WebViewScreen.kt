package top.yogiczy.mytv.tv.ui.screens.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.tv.ui.material.Visible
import top.yogiczy.mytv.tv.ui.screens.webview.components.WebViewPlaceholder
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    urlProvider: () -> String = { "${ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX}https://tv.cctv.com/live/index.shtml" },
    onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val url = urlProvider().replace(ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX, "")
    // 使用 isLoading 狀態來控制加載動畫和 WebView 的可見性
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .background(Color.Black)
                .alpha(if (isLoading) 0f else 1f),
            factory = {
                MyWebView(it).apply {
                    webViewClient = MyClient(
                        onPageStarted = { isLoading = true },
                    )

                    setBackgroundColor(Color.Black.toArgb())
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // 提速優化 1：禁止自動加載圖片和攔截網絡圖片
                        loadsImagesAutomatically = false
                        blockNetworkImage = true

                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        mediaPlaybackRequiresUserGesture = false
                    }

                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false

                    addJavascriptInterface(
                        MyWebViewInterface(
                            onVideoResolutionChanged = { width, height ->
                                isLoading = false
                                onVideoResolutionChanged(width, height)
                            },
                        ), "Android"
                    )
                }
            },
            update = { it.loadUrl(url) },
        )

        Visible({ isLoading }) { WebViewPlaceholder() }
    }
}

class MyClient(
    private val onPageStarted: () -> Unit,
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    // 提速優化 2：攔截無用資源的請求，極大縮短網頁加載時間
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val reqUrl = request?.url?.toString() ?: ""
        val blockKeywords = listOf(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", // 屏蔽圖片
            "google-analytics", "hm.baidu.com", "umeng.com",  // 屏蔽常見的統計腳本
            "adsystem", "doubleclick"                         // 屏蔽常見廣告
        )

        if (blockKeywords.any { reqUrl.contains(it, ignoreCase = true) }) {
            // 返回一個空的響應，直接掐斷該網絡請求
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView, url: String) {
        view.evaluateJavascript(
            """
            ;(async () => {
                // ==========================================
                // 1. 全局注入樣式：暴力霸屏，遮擋原生網頁
                // ==========================================
                const style = document.createElement('style');
                style.innerHTML = `
                    /* 隱藏整個網頁自帶的滾動條 */
                    html, body { width: 100vw !important; height: 100vh !important; background: #000 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; }
                    
                    /* 終極 video 霸屏 CSS */
                    video {
                        width: 100vw !important; height: 100vh !important;
                        position: fixed !important; top: 0 !important; left: 0 !important; bottom: 0 !important; right: 0 !important;
                        z-index: 2147483647 !important; background: #000 !important;
                        max-width: none !important; max-height: none !important;
                        transform: none !important; object-fit: contain !important;
                        border: none !important; margin: 0 !important; padding: 0 !important;
                        display: block !important; visibility: visible !important; opacity: 1 !important;
                    }
                    
                    /* 遮罩層，阻擋原生網頁的點擊暫停事件 */
                    #ytv-mask {
                        width: 100vw !important; height: 100vh !important;
                        position: fixed !important; top: 0 !important; left: 0 !important;
                        z-index: 2147483648 !important; background: transparent !important;
                    }
                `;
                document.head.appendChild(style);

                // 2. 創建防誤觸遮罩
                if(!document.getElementById('ytv-mask')) {
                    const mask = document.createElement('div');
                    mask.id = 'ytv-mask';
                    mask.addEventListener('click', (e) => { e.stopPropagation(); e.preventDefault(); });
                    document.body.appendChild(mask);
                }

                let isReady = false;

                // ==========================================
                // 3. 動態維護定時器：擊碎 CCTV 等網頁的層疊上下文陷阱
                // ==========================================
                setInterval(() => {
                    const videoEl = document.querySelector('video');
                    if (!videoEl) return;

                    // 持續剝除父元素的各種限制，特別是 z-index 和 position 陷阱
                    let p = videoEl.parentElement;
                    while(p && p !== document.body && p !== document.documentElement) {
                        const style = window.getComputedStyle(p);
                        
                        // 移除會導致 position: fixed 降級或被裁切的屬性
                        if (style.transform !== 'none') p.style.setProperty('transform', 'none', 'important');
                        if (style.filter !== 'none') p.style.setProperty('filter', 'none', 'important');
                        if (style.contain !== 'none') p.style.setProperty('contain', 'none', 'important');
                        if (style.clipPath !== 'none') p.style.setProperty('clip-path', 'none', 'important');
                        
                        // 致命一擊：移除父級的透明度、層級和定位，防止 video 被困在低 z-index 的圖層中 (解決CCTV無法全屏的核心)
                        if (style.opacity !== '1') p.style.setProperty('opacity', '1', 'important');
                        if (style.zIndex !== 'auto') p.style.setProperty('z-index', 'auto', 'important');
                        if (style.position !== 'static') p.style.setProperty('position', 'static', 'important');
                        
                        p = p.parentElement;
                    }

                    // 安全觸發播放：捕獲 Promise 異常
                    if (videoEl.paused) {
                        const playPromise = videoEl.play();
                        if (playPromise !== undefined) {
                            playPromise.catch(e => { /* 忽略攔截導致的 AbortError */ });
                        }
                    }

                    // 探測首幀就緒狀態，通知 Android 端去掉加載動畫
                    if (!isReady && videoEl.videoWidth > 0 && videoEl.videoHeight > 0) {
                        isReady = true;
                        Android.changeVideoResolution(videoEl.videoWidth, videoEl.videoHeight);
                    }
                }, 300);

                // ==========================================
                // 4. 防死鎖兜底：5秒後若仍未上報分辨率則強制解鎖
                // ==========================================
                setTimeout(() => {
                    if (!isReady) {
                        isReady = true;
                        Android.changeVideoResolution(1920, 1080);
                    }
                }, 5000);
            })();
            """.trimIndent()
        ) {}
    }
}

class MyWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }
}

class MyWebViewInterface(
    private val onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun changeVideoResolution(width: Int, height: Int) {
        mainHandler.post {
            onVideoResolutionChanged(width, height)
        }
    }
}