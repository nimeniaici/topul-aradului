package ro.hack4arad.topul.data

import ro.hack4arad.topul.util.runCatching
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ro.hack4arad.topul.BuildConfig
import java.io.File

/**
 * Dacă aplicația crapă, scriem eroarea într-un fișier; la pornirea următoare o trimitem serverului
 * (fără date personale: doar eroarea, modelul telefonului și versiunea), ca s-o putem repara.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(Log.getStackTraceString(error).take(20000)) }
            previous?.uncaughtException(thread, error)
        }
        if (file.exists()) sendAndDelete(file)
    }

    private fun sendAndDelete(file: File) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val body = JSONObject()
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    .put("trace", file.readText())
                val request = Request.Builder()
                    .url(Network.baseUrl + "debug/crash")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                OkHttpClient().newCall(request).execute().use { if (it.isSuccessful) file.delete() }
            }
        }
    }
}
