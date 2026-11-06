package ro.hack4arad.topul.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ține minte token-ul de login între porniri ale aplicației. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _token = MutableStateFlow(prefs.getString("token", null))
    val token: StateFlow<String?> = _token

    val userId: Int get() = prefs.getInt("user_id", -1)
    val userName: String? get() = prefs.getString("user_name", null)

    fun save(t: TokenOut) {
        prefs.edit()
            .putString("token", t.accessToken)
            .putInt("user_id", t.user.id)
            .putString("user_name", t.user.name)
            .apply()
        _token.value = t.accessToken
    }

    fun updateName(name: String) {
        prefs.edit().putString("user_name", name).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _token.value = null
    }
}
