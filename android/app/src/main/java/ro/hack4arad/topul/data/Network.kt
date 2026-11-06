package ro.hack4arad.topul.data

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ro.hack4arad.topul.BuildConfig
import java.util.concurrent.TimeUnit

/** Locul unic de unde ecranele iau API-ul, sesiunea și fluxul live. */
object Network {
    val baseUrl: String = BuildConfig.BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    lateinit var session: Session
        private set
    lateinit var api: Api
        private set
    lateinit var live: LiveClient
        private set

    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    fun init(context: Context) {
        session = Session(context.applicationContext)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = session.token.value
                val request = if (token != null) {
                    original.newBuilder().header("Authorization", "Bearer $token").build()
                } else original
                val response = chain.proceed(request)
                // token expirat -> înapoi la login
                if (response.code == 401 && token != null && !original.url.encodedPath.startsWith("/auth")) {
                    session.logout()
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(Api::class.java)

        live = LiveClient(client, baseUrl.replaceFirst("http", "ws") + "live", gson).also { it.start() }
    }

    /** "/uploads/x.jpg" -> "http://server/uploads/x.jpg" */
    fun fullUrl(path: String?): String? = when {
        path == null -> null
        path.startsWith("http") -> path
        else -> baseUrl.trimEnd('/') + path
    }
}
