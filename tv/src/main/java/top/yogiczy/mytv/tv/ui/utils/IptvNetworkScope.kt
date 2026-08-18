package top.yogiczy.mytv.tv.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/** 只記錄網絡類型，不保存 SSID、IP 或其他可識別資料。 */
enum class IptvNetworkScope {
    VPN,
    ETHERNET,
    WIFI,
    CELLULAR,
    OTHER,
    UNKNOWN,
}

internal fun isIptvNetworkHandover(
    attemptScope: IptvNetworkScope,
    currentScope: IptvNetworkScope,
): Boolean = attemptScope != IptvNetworkScope.UNKNOWN &&
    currentScope != IptvNetworkScope.UNKNOWN &&
    attemptScope != currentScope

fun currentIptvNetworkScope(context: Context): IptvNetworkScope {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return IptvNetworkScope.UNKNOWN
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return legacyIptvNetworkScope(manager)
    }
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        ?: return IptvNetworkScope.UNKNOWN
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> IptvNetworkScope.VPN
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> IptvNetworkScope.ETHERNET
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> IptvNetworkScope.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> IptvNetworkScope.CELLULAR
        else -> IptvNetworkScope.OTHER
    }
}

@Suppress("DEPRECATION")
private fun legacyIptvNetworkScope(manager: ConnectivityManager): IptvNetworkScope =
    when (manager.activeNetworkInfo?.type) {
        ConnectivityManager.TYPE_VPN -> IptvNetworkScope.VPN
        ConnectivityManager.TYPE_ETHERNET -> IptvNetworkScope.ETHERNET
        ConnectivityManager.TYPE_WIFI -> IptvNetworkScope.WIFI
        ConnectivityManager.TYPE_MOBILE -> IptvNetworkScope.CELLULAR
        null -> IptvNetworkScope.UNKNOWN
        else -> IptvNetworkScope.OTHER
    }
