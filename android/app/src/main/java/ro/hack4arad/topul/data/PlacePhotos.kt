package ro.hack4arad.topul.data

import ro.hack4arad.topul.util.runCatching
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place as GPlace
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import ro.hack4arad.topul.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Pozele reale ale locurilor, de pe Google Maps (Places API New).
 * Lazy loading: o poză se cere doar când locul apare pe ecran, apoi adresa ei se ține minte
 * (în memorie + pe telefon, 12 ore), ca să nu plătim de două ori aceeași cerere.
 */
object PlacePhotos {
    private const val TTL_MS = 12 * 60 * 60 * 1000L
    private const val NONE = "-"

    private var client: PlacesClient? = null
    private lateinit var prefs: SharedPreferences
    private val memory = ConcurrentHashMap<Int, String>()
    private val locks = ConcurrentHashMap<Int, Mutex>()

    val enabled: Boolean get() = client != null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("place_photos_v2", Context.MODE_PRIVATE)
        if (BuildConfig.MAPS_API_KEY.isBlank()) return
        runCatching {
            if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.MAPS_API_KEY)
            client = Places.createClient(context.applicationContext)
        }
    }

    /** Adresa pozei (sau null dacă Google nu are poză). Sigur de apelat de oriunde, din mai multe locuri deodată. */
    suspend fun url(id: Int, name: String, lat: Double, lng: Double): String? {
        memory[id]?.let { return it.takeIf { it != NONE } }
        val cached = prefs.getString("u$id", null)
        if (cached != null && System.currentTimeMillis() - prefs.getLong("t$id", 0) < TTL_MS) {
            memory[id] = cached
            return cached.takeIf { it != NONE }
        }
        val places = client ?: return null
        return locks.getOrPut(id) { Mutex() }.withLock {
            memory[id]?.let { return@withLock it.takeIf { it != NONE } }
            // o eroare (fără net, cheie refuzată) NU se ține minte; doar „Google chiar n-are poză”
            val found = try { fetch(places, name, lat, lng) } catch (e: Exception) {
                Log.w("PlacePhotos", "Poza pentru '$name' a eșuat: ${e.message}")
                return@withLock null
            }
            val value = found ?: NONE
            memory[id] = value
            prefs.edit().putString("u$id", value).putLong("t$id", System.currentTimeMillis()).apply()
            found
        }
    }

    private suspend fun fetch(places: PlacesClient, name: String, lat: Double, lng: Double): String? {
        val request = SearchByTextRequest.builder("$name, Arad", listOf(GPlace.Field.ID, GPlace.Field.PHOTO_METADATAS))
            .setMaxResultCount(1)
            .setLocationBias(CircularBounds.newInstance(LatLng(lat, lng), 300.0))
            .build()
        val place = places.searchByText(request).await().places.firstOrNull() ?: return null
        val photo = place.photoMetadatas?.firstOrNull() ?: return null
        val resolved = places.fetchResolvedPhotoUri(
            FetchResolvedPhotoUriRequest.builder(photo).setMaxWidth(900).setMaxHeight(900).build()
        ).await()
        return resolved.uri?.toString()
    }
}
