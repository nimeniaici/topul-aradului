package ro.hack4arad.topul.util

import ro.hack4arad.topul.util.runCatching
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/** Transformă o eroare într-un mesaj pe înțelesul utilizatorului. */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val detail = body?.let {
            runCatching {
                val el = JsonParser.parseString(it).asJsonObject.get("detail")
                when {
                    el == null -> null
                    el.isJsonPrimitive -> el.asString
                    el.isJsonArray -> el.asJsonArray.firstOrNull()?.asJsonObject?.get("msg")?.asString
                    else -> null
                }
            }.getOrNull()
        }
        detail ?: "Eroare de la server (${code()})."
    }
    is IOException -> "Nu mă pot conecta la server. Verifică BASE_URL și că backend-ul rulează."
    else -> message ?: "A apărut o eroare."
}
