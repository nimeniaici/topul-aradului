package ro.hack4arad.topul.util

import android.content.Context
import ro.hack4arad.topul.data.LatLngIn
import ro.hack4arad.topul.data.Network

/**
 * Partajarea poziției cu prietenii (opțională, oprită implicit). Poziția se trimite doar cât aplicația e deschisă
 * (bucla din AppRoot), doar dacă utilizatorul a pornit-o și a dat permisiunea de locație.
 */
object LocationShare {
    private const val KEY = "share_location"

    fun isEnabled(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    suspend fun pushNow(context: Context) {
        if (!isEnabled(context) || !hasLocationPermission(context)) return
        val fix = runCatching { currentFix(context) }.getOrNull() ?: return
        runCatching { Network.api.putLocation(LatLngIn(fix.lat, fix.lng)) }
    }
}
