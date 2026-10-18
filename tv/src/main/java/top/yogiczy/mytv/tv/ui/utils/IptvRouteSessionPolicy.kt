package top.yogiczy.mytv.tv.ui.utils

internal class IptvRouteSessionPolicy {
    var manualRouteIndex: Int? = null
        private set
    fun beginSession(requestedIndex: Int?) {
        manualRouteIndex = requestedIndex
    }

    fun selectManual(index: Int) {
        manualRouteIndex = index
    }

    fun allowsSoftSwitch(currentIndex: Int): Boolean = currentIndex != manualRouteIndex

}
