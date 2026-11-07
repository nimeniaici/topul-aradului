package ro.hack4arad.topul.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Limba aplicației (română, engleză, maghiară). Se schimbă din Setări și se ține minte pe telefon. */
enum class Lang(val code: String, val label: String) { RO("ro", "Română"), EN("en", "English"), HU("hu", "Magyar") }

object LangState {
    var current by mutableStateOf(Lang.RO)
        private set

    fun init(context: Context) {
        val code = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "ro")
        current = Lang.entries.firstOrNull { it.code == code } ?: Lang.RO
    }

    fun select(context: Context, lang: Lang) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", lang.code).apply()
        current = lang
    }
}

/** Traducere scurtă: tr("Hartă", "Map", "Térkép"). */
fun tr(ro: String, en: String, hu: String): String = when (LangState.current) {
    Lang.RO -> ro
    Lang.EN -> en
    Lang.HU -> hu
}
