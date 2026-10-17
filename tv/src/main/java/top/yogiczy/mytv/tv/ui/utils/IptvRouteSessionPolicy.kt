package top.yogiczy.mytv.tv.ui.utils

internal class IptvRouteSessionPolicy {
    var manualRouteIndex: Int? = null
        private set
    var relayPassStarted: Boolean = false
        private set

    fun beginSession(requestedIndex: Int?) {
        manualRouteIndex = requestedIndex
        relayPassStarted = false
    }

    fun selectManual(index: Int) {
        manualRouteIndex = index
    }

    fun allowsSoftSwitch(currentIndex: Int): Boolean = currentIndex != manualRouteIndex

    fun tryStartRelayPass(preferenceId: String, isSuperAdmin: Boolean): Boolean {
        if (preferenceId != "auto" || !isSuperAdmin || relayPassStarted) return false
        relayPassStarted = true
        return true
    }
}
