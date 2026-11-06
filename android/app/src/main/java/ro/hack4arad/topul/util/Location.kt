package ro.hack4arad.topul.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class Fix(val lat: Double, val lng: Double, val isMock: Boolean)

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/** Locația curentă (sau ultima cunoscută). Null dacă GPS-ul nu răspunde. */
@SuppressLint("MissingPermission")
suspend fun currentFix(context: Context): Fix? {
    if (!hasLocationPermission(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    val location = runCatching {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
    }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null

    val mock = if (Build.VERSION.SDK_INT >= 31) location.isMock
    else @Suppress("DEPRECATION") location.isFromMockProvider
    return Fix(location.latitude, location.longitude, mock)
}
