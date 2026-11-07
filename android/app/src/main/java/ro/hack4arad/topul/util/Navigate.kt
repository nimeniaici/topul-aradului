package ro.hack4arad.topul.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * „Du-mă acolo”: pornește navigarea în Google Maps (pe jos, implicit), ca în Maps.
 * Dacă Google Maps nu e instalat, deschide traseul în browser (sau în altă aplicație de hărți).
 */
fun navigateTo(context: Context, lat: Double, lng: Double, mode: String = "w") {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=$mode"))
        .setPackage("com.google.android.apps.maps")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val travel = when (mode) { "d" -> "driving"; "b" -> "bicycling"; "l" -> "transit"; else -> "walking" }
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=$travel"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(nav)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(web) }
    }
}

/** Cât faci pe jos (aprox. 5 km/h, în linie dreaptă × 1,3 pentru străzi). */
fun walkingMinutes(distanceM: Double): Int = maxOf(1, (distanceM * 1.3 / 83).toInt())
