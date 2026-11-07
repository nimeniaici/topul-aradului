package ro.hack4arad.topul.ui.components

import ro.hack4arad.topul.ui.theme.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted

/** Ce se poate tasta într-un username: litere mici fără diacritice, cifre, „.” și „_”, max. 20. */
fun cleanUsername(raw: String): String =
    java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
        .take(20)

/**
 * Câmpul de username, cu verificare pe loc la server („liber” / „deja luat”, cu sugestie).
 * [onValid] primește true doar când serverul a confirmat că username-ul e liber
 * (sau e chiar username-ul tău actual, [current]).
 */
@Composable
fun UsernameField(
    value: String,
    onChange: (String) -> Unit,
    onValid: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    current: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        suggestion = null
        when {
            value.isEmpty() -> { status = null; ok = false }
            value == current -> { status = "Ăsta e username-ul tău"; ok = true }
            value.length < 3 -> { status = "Minim 3 caractere"; ok = false }
            else -> {
                ok = false
                status = t("Verific…")
                onValid(false)
                delay(400) // așteptăm să termini de scris
                runCatching { Network.api.usernameAvailable(value) }
                    .onSuccess { r ->
                        ok = r.available
                        status = if (r.available) t("Liber, e al tău!") else (r.reason ?: t("Indisponibil"))
                        suggestion = r.suggestion
                    }
                    .onFailure { status = "Nu am putut verifica acum"; ok = false }
            }
        }
        onValid(ok)
    }

    OutlinedTextField(
        value = value,
        onValueChange = { onChange(cleanUsername(it)) },
        label = { Text(t("Username (unic)")) },
        prefix = { Text("@", color = Gold, fontWeight = FontWeight.Bold) },
        placeholder = { Text("ex. diana.arad", color = TextMuted) },
        singleLine = true,
        supportingText = {
            val s = status
            if (s != null) {
                val color = if (ok) Mint else if (s == t("Verific…")) TextMuted else Pink
                Text(
                    if (suggestion != null) "$s Încearcă @$suggestion" else s,
                    color = color,
                    modifier = if (suggestion != null) Modifier.clickable { onChange(suggestion!!) } else Modifier,
                )
            } else {
                Text(t("Litere mici, cifre, „.” sau „_”. Așa te văd ceilalți în joc."), color = TextMuted)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false, imeAction = ImeAction.Next,
        ),
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
    )
}
