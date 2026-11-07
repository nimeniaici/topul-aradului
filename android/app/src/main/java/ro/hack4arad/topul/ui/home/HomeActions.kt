package ro.hack4arad.topul.ui.home

/**
 * Ce a cerut utilizatorul din meniul „Acasă” pentru ecranul Hartă
 * (ex.: deschide direct lista, filtrul „Obiective” sau formularul de sesizare).
 */
data class MapRequest(val list: Boolean = false, val category: String? = null, val report: Boolean = false, val emergency: Boolean = false)

object HomeActions {
    @Volatile var pendingMap: MapRequest? = null
    fun takeMap(): MapRequest? = pendingMap.also { pendingMap = null }
}
