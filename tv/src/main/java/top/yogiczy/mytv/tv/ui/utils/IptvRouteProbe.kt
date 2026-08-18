package top.yogiczy.mytv.tv.ui.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.network.await
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

internal enum class IptvRouteProbeStatus {
    AVAILABLE,
    INCONCLUSIVE,
    UNAVAILABLE,
}

internal data class IptvRouteProbeResult(
    val routeIndex: Int,
    val status: IptvRouteProbeStatus,
    val elapsedMs: Long = Long.MAX_VALUE,
)

internal data class HlsProbeTarget(
    val value: String,
    val isPlaylist: Boolean,
)

/**
 * 輕量檢查同一頻道嘅候選線路。只讀 playlist 同少量 segment bytes，唔建立額外 decoder，
 * 亦唔把結果寫入長期健康分；真正成功仍以播放器首幀為準。
 */
internal object IptvRouteProbe {
    private const val MAX_CONCURRENT_PROBES = 3
    private const val ROUTE_PROBE_TIMEOUT_MS = 3_000L
    private const val PLAYLIST_READ_LIMIT = 64 * 1024
    private const val MEDIA_READ_LIMIT = 32 * 1024

    private val baseClient by lazy {
        OkHttp.client.newBuilder()
            .connectTimeout(1_200, TimeUnit.MILLISECONDS)
            .readTimeout(1_200, TimeUnit.MILLISECONDS)
            .callTimeout(2_800, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    suspend fun probeAll(routes: List<ChannelRoute>): List<IptvRouteProbeResult> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        routes.mapIndexed { index, route ->
            async(Dispatchers.IO) {
                semaphore.withPermit { probe(index, route) }
            }
        }.awaitAll()
    }

    private suspend fun probe(index: Int, route: ChannelRoute): IptvRouteProbeResult {
        val scheme = runCatching { URI(route.url).scheme?.lowercase() }.getOrNull()
        if (scheme !in setOf("http", "https")) {
            return IptvRouteProbeResult(index, IptvRouteProbeStatus.INCONCLUSIVE)
        }

        val startedAt = System.nanoTime()
        val routeClient = baseClient.newBuilder()
            .cookieJar(InMemoryProbeCookieJar())
            .build()
        val status = withTimeoutOrNull(ROUTE_PROBE_TIMEOUT_MS) {
            probeHttpRoute(route, routeClient)
        } ?: IptvRouteProbeStatus.INCONCLUSIVE
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        return IptvRouteProbeResult(index, status, elapsedMs)
    }

    private suspend fun probeHttpRoute(
        route: ChannelRoute,
        routeClient: OkHttpClient,
    ): IptvRouteProbeStatus {
        val initialResponse = fetch(
            routeClient,
            route.url,
            route,
            PLAYLIST_READ_LIMIT,
            ranged = false,
        )
        if (initialResponse !is FetchResult.Success) return initialResponse.toProbeStatus()
        if (initialResponse.looksLikeErrorDocument()) return IptvRouteProbeStatus.UNAVAILABLE
        var response: FetchResult.Success = initialResponse

        repeat(2) {
            val text = response.bytes.toString(Charsets.UTF_8)
            if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                return if (response.bytes.isNotEmpty()) {
                    IptvRouteProbeStatus.AVAILABLE
                } else {
                    IptvRouteProbeStatus.UNAVAILABLE
                }
            }

            val target = hlsProbeTarget(text) ?: return IptvRouteProbeStatus.UNAVAILABLE
            val targetUrl = resolveProbeUrl(response.finalUrl, target.value)
                ?: return IptvRouteProbeStatus.UNAVAILABLE
            val nextResponse = fetch(
                client = routeClient,
                url = targetUrl,
                route = route,
                limit = if (target.isPlaylist) PLAYLIST_READ_LIMIT else MEDIA_READ_LIMIT,
                ranged = !target.isPlaylist,
            )
            if (nextResponse !is FetchResult.Success) return nextResponse.toProbeStatus()
            if (nextResponse.looksLikeErrorDocument()) return IptvRouteProbeStatus.UNAVAILABLE
            response = nextResponse
            if (!target.isPlaylist) {
                return if (response.bytes.isNotEmpty()) {
                    IptvRouteProbeStatus.AVAILABLE
                } else {
                    IptvRouteProbeStatus.UNAVAILABLE
                }
            }
        }

        return IptvRouteProbeStatus.INCONCLUSIVE
    }

    private suspend fun fetch(
        client: OkHttpClient,
        url: String,
        route: ChannelRoute,
        limit: Int,
        ranged: Boolean,
    ): FetchResult {
        val request = runCatching {
            Request.Builder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-cache")
                .apply {
                    route.requestHeaders.forEach { (name, value) -> header(name, value) }
                    if (ranged) header("Range", "bytes=0-${limit - 1}")
                }
                .build()
        }.getOrElse { return FetchResult.DefinitiveFailure }

        val result = runCatching {
            client.newCall(request).await().use { response ->
                if (ranged && response.code in setOf(400, 405, 416)) {
                    return@use FetchResult.RangeUnsupported
                }
                if (!response.isSuccessful) return@use FetchResult.DefinitiveFailure
                val body = response.body ?: return@use FetchResult.DefinitiveFailure
                FetchResult.Success(
                    finalUrl = response.request.url.toString(),
                    contentType = body.contentType()?.toString().orEmpty(),
                    bytes = body.byteStream().use { input ->
                        val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
                        val buffer = ByteArray(8 * 1024)
                        while (output.size() < limit) {
                            val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    },
                )
            }
        }.getOrElse { FetchResult.Inconclusive }
        return if (ranged && result == FetchResult.RangeUnsupported) {
            fetch(client, url, route, limit, ranged = false)
        } else {
            result
        }
    }

    private sealed interface FetchResult {
        data class Success(
            val finalUrl: String,
            val contentType: String,
            val bytes: ByteArray,
        ) : FetchResult {
            fun looksLikeErrorDocument(): Boolean {
                val prefix = bytes.toString(Charsets.UTF_8).trimStart().take(64).lowercase()
                return contentType.contains("text/html", ignoreCase = true) ||
                    contentType.contains("application/json", ignoreCase = true) ||
                    prefix.startsWith("<!doctype html") ||
                    prefix.startsWith("<html") ||
                    prefix.startsWith("{\"error\"")
            }
        }

        data object DefinitiveFailure : FetchResult
        data object Inconclusive : FetchResult
        data object RangeUnsupported : FetchResult
    }

    private fun FetchResult.toProbeStatus(): IptvRouteProbeStatus = when (this) {
        is FetchResult.Success -> IptvRouteProbeStatus.AVAILABLE
        FetchResult.DefinitiveFailure -> IptvRouteProbeStatus.UNAVAILABLE
        FetchResult.Inconclusive -> IptvRouteProbeStatus.INCONCLUSIVE
        FetchResult.RangeUnsupported -> IptvRouteProbeStatus.INCONCLUSIVE
    }
}

private class InMemoryProbeCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { incoming ->
            this.cookies.removeAll { existing ->
                existing.name == incoming.name &&
                    existing.domain == incoming.domain &&
                    existing.path == incoming.path
            }
            if (incoming.expiresAt > now) this.cookies += incoming
        }
        this.cookies.removeAll { it.expiresAt <= now }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }
}

internal fun hlsProbeTarget(playlist: String): HlsProbeTarget? {
    val lines = playlist.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val variants = lines.mapIndexedNotNull { index, line ->
        if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) return@mapIndexedNotNull null
        val bandwidth = Regex("(?:AVERAGE-)?BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L
        val value = lines.drop(index + 1).firstOrNull { !it.startsWith('#') }
            ?: return@mapIndexedNotNull null
        bandwidth to value
    }
    variants.maxByOrNull { it.first }?.let { (_, value) ->
        return HlsProbeTarget(value, isPlaylist = true)
    }

    val segments = lines.filter { !it.startsWith('#') }
    // Live edge 最新一段經常仍未同步到免費 CDN；優先測上一段，減少 404 誤判。
    val segment = segments.getOrNull(segments.lastIndex - 1)
        ?: segments.lastOrNull()
        ?: return null
    return HlsProbeTarget(segment, isPlaylist = false)
}

internal fun remainingProbeViableRoutes(
    attemptOrder: List<Int>,
    currentCursor: Int,
    results: List<IptvRouteProbeResult>,
): List<Int> {
    if (currentCursor !in attemptOrder.indices) return emptyList()
    val resultByIndex = results.associateBy(IptvRouteProbeResult::routeIndex)
    return attemptOrder.drop(currentCursor + 1).filter { index ->
        resultByIndex[index]?.status != IptvRouteProbeStatus.UNAVAILABLE
    }
}

/**
 * 軟卡頓時只回訪已即時確認可用、畫質更高，或同畫質且長期分數明顯更佳嘅線路。
 * 呢個決策唔會降低畫質，亦唔會把一次 probe 當成持久成功紀錄。
 */
internal fun selectSoftRevisitRoute(
    routes: List<ChannelRoute>,
    currentRouteIndex: Int,
    results: List<IptvRouteProbeResult>,
    learnedScores: Map<Int, Double> = emptyMap(),
    supports4k: Boolean = true,
): Int? {
    val currentRoute = routes.getOrNull(currentRouteIndex) ?: return null
    val currentQualityRank = effectiveQualityRank(currentRoute, supports4k)
    val currentScore = learnedScores[currentRouteIndex] ?: 50.0
    val resultByIndex = results.associateBy(IptvRouteProbeResult::routeIndex)

    return routes.indices.asSequence()
        .filter { it != currentRouteIndex }
        .filter { resultByIndex[it]?.status == IptvRouteProbeStatus.AVAILABLE }
        .filter { index ->
            val candidateQualityRank = effectiveQualityRank(routes[index], supports4k)
            candidateQualityRank > currentQualityRank ||
                (
                    candidateQualityRank == currentQualityRank &&
                        IptvRouteHealthStore.shouldAutoSwitch(
                            currentScore,
                            learnedScores[index] ?: 50.0,
                        )
                    )
        }
        .sortedWith(
            compareByDescending<Int> { effectiveQualityRank(routes[it], supports4k) }
                .thenByDescending { learnedScores[it] ?: 50.0 }
                .thenBy { resultByIndex[it]?.elapsedMs ?: Long.MAX_VALUE }
                .thenBy { it },
        )
        .firstOrNull()
}

internal fun reorderUnattemptedRoutesByProbe(
    routes: List<ChannelRoute>,
    attemptOrder: List<Int>,
    currentCursor: Int,
    results: List<IptvRouteProbeResult>,
    learnedScores: Map<Int, Double> = emptyMap(),
    supports4k: Boolean = true,
): List<Int> {
    if (attemptOrder.isEmpty() || currentCursor !in attemptOrder.indices) return attemptOrder
    val resultByIndex = results.associateBy(IptvRouteProbeResult::routeIndex)
    val sourceRank = attemptOrder.withIndex().associate { (rank, index) -> index to rank }
    val prefix = attemptOrder.take(currentCursor + 1)
    val tail = attemptOrder.drop(currentCursor + 1).sortedWith(
        liveProbeComparator(routes, resultByIndex, learnedScores, sourceRank, supports4k),
    )
    return prefix + tail
}

internal fun orderRoutesByProbe(
    routes: List<ChannelRoute>,
    attemptOrder: List<Int>,
    results: List<IptvRouteProbeResult>,
    learnedScores: Map<Int, Double> = emptyMap(),
    supports4k: Boolean = true,
): List<Int> {
    val resultByIndex = results.associateBy(IptvRouteProbeResult::routeIndex)
    val sourceRank = attemptOrder.withIndex().associate { (rank, index) -> index to rank }
    return attemptOrder.sortedWith(
        liveProbeComparator(routes, resultByIndex, learnedScores, sourceRank, supports4k),
    )
}

private fun liveProbeComparator(
    routes: List<ChannelRoute>,
    resultByIndex: Map<Int, IptvRouteProbeResult>,
    learnedScores: Map<Int, Double>,
    sourceRank: Map<Int, Int>,
    supports4k: Boolean,
): Comparator<Int> = compareBy<Int> { index ->
    when (resultByIndex[index]?.status) {
        IptvRouteProbeStatus.AVAILABLE -> 0
        IptvRouteProbeStatus.INCONCLUSIVE, null -> 1
        IptvRouteProbeStatus.UNAVAILABLE -> 2
    }
}.thenByDescending { index ->
    routes.getOrNull(index)?.let { effectiveQualityRank(it, supports4k) } ?: 0
}.thenByDescending { index -> learnedScores[index] ?: 50.0 }
    .thenBy { index -> resultByIndex[index]?.elapsedMs ?: Long.MAX_VALUE }
    .thenBy { index -> sourceRank[index] ?: Int.MAX_VALUE }

private fun effectiveQualityRank(route: ChannelRoute, supports4k: Boolean): Int =
    if (
        !supports4k &&
        route.quality == top.yogiczy.mytv.core.data.entities.channel.ChannelQuality.UHD_4K
    ) {
        -1
    } else {
        route.quality.rank
    }

private fun resolveProbeUrl(baseUrl: String, target: String): String? = runCatching {
    URI(baseUrl).resolve(target.trim()).toString()
}.getOrNull()
