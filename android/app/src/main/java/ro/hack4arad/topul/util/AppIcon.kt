package ro.hack4arad.topul.util

import ro.hack4arad.topul.util.runCatching
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Iconița de pe ecranul telefonului urmează paleta aleasă: în manifest sunt 5 „activity-alias”,
 * câte unul pentru fiecare logo; îl activăm pe cel al paletei și le dezactivăm pe celelalte.
 * (Launcher-ul poate avea nevoie de câteva secunde ca să schimbe iconița.)
 */
object AppIcon {
    private val ALIASES = mapOf(1 to "LauncherArgint", 2 to "LauncherAmurg", 3 to "LauncherTrandafir", 4 to "LauncherCaramida", 5 to "LauncherMures")

    fun apply(context: Context, paletteId: Int) {
        val pm = context.packageManager
        val chosen = ALIASES[paletteId] ?: return
        // întâi îl pornim pe cel nou (ca aplicația să aibă mereu o iconiță), apoi le oprim pe celelalte
        val order = listOf(chosen) + ALIASES.values.filter { it != chosen }
        for (name in order) {
            val component = ComponentName(context.packageName, "${context.packageName}.$name")
            val state = if (name == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(component) != state) {
                runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
            }
        }
    }
}
