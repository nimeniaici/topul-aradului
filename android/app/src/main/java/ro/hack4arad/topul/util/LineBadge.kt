package ro.hack4arad.topul.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

private val cache = HashMap<String, BitmapDescriptor>()

/** Insigna de pe hartă pentru un vehicul CTP: numărul liniei pe culoarea ei (tramvai = pătrat rotunjit, autobuz = pastilă). */
fun lineBadge(ref: String, colorHex: String, tram: Boolean): BitmapDescriptor = cache.getOrPut("$ref|$colorHex|$tram") {
    val density = 2.6f
    val text = ref.take(8)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 13f * density; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val w = maxOf(30f * density, tp.measureText(text) + 14f * density)
    val h = 24f * density
    val bmp = Bitmap.createBitmap(w.toInt() + 6, h.toInt() + 6, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = RectF(3f, 3f, w + 3f, h + 3f)
    val radius = if (tram) 7f * density else h / 2
    c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    val inner = RectF(r.left + 2.5f * density / 2, r.top + 2.5f * density / 2, r.right - 2.5f * density / 2, r.bottom - 2.5f * density / 2)
    val fill = runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(0xFF455A64.toInt())
    c.drawRoundRect(inner, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
    c.drawText(text, r.centerX(), r.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
    BitmapDescriptorFactory.fromBitmap(bmp)
}
