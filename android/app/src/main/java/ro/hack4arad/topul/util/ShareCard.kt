package ro.hack4arad.topul.util

import ro.hack4arad.topul.util.runCatching
import ro.hack4arad.topul.ui.theme.ThemeState
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.PlacePhotos
import java.io.File
import kotlin.math.max

/**
 * Card de Instagram Story (1080×1920): poza locului, „Sunt Primarul la …”, username-ul și linkul aplicației.
 * Se trimite prin meniul de share al telefonului (Instagram, WhatsApp, Facebook...).
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1920
    // culorile vin din paleta aleasă: fundal închis + nuanța cea mai deschisă
    private val NIGHT get() = ThemeState.palette.deep.toArgb()
    private val GOLD get() = ThemeState.palette.a4.toArgb()
    private val LILAC get() = ThemeState.palette.a3.toArgb()

    suspend fun share(context: Context, placeId: Int, mayor: Boolean): Boolean {
        val file = runCatching { render(context, placeId, mayor) }.getOrNull() ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val link = Network.baseUrl + "app"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Joacă și tu Topul Aradului în CityConnect Arad: $link")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Arată-le prietenilor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private suspend fun render(context: Context, placeId: Int, mayor: Boolean): File = withContext(Dispatchers.IO) {
        val place = Network.api.place(placeId)
        val username = Network.session.userName ?: "arădean"
        val photoUrl = Network.fullUrl(place.photoUrl ?: place.recentPhotos.firstOrNull()?.photoUrl)
            ?: PlacePhotos.url(place.id, place.name, place.lat, place.lng)
        val photo = photoUrl?.let { loadBitmap(context, it) }

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(NIGHT)

        // poza locului, sus, cu colțuri rotunjite și o trecere lină spre fundal
        val photoRect = RectF(60f, 160f, W - 60f, 1180f)
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(photoRect.width() / photo.width, photoRect.height() / photo.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(photoRect.left + (photoRect.width() - photo.width * scale) / 2,
                    photoRect.top + (photoRect.height() - photo.height * scale) / 2)
            })
            c.drawRoundRect(photoRect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            c.drawRoundRect(photoRect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ThemeState.palette.a1.toArgb() })
        }
        c.drawRoundRect(photoRect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, photoRect.top + 500f, 0f, photoRect.bottom, NIGHT and 0x00FFFFFF, NIGHT, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(photoRect, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 8f; color = GOLD
        })

        // antetul
        val brand = text(46f, GOLD, bold = true).apply { letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
        c.drawText("CITYCONNECT ARAD", W / 2f, 110f, brand)

        // mesajul principal
        val headline = if (mayor) "Sunt Primarul la" else "Am fost la"
        c.drawText(headline, W / 2f, 1290f, text(64f, LILAC, bold = false).apply { textAlign = Paint.Align.CENTER })
        val nameLayout = layout(place.name, text(108f, GOLD, bold = true), W - 140, maxLines = 2)
        c.save(); c.translate(70f, 1320f); nameLayout.draw(c); c.restore()

        val after = 1320f + nameLayout.height + 90f
        c.drawText("@$username", W / 2f, after, text(60f, 0xFFFFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER })
        c.drawText(
            if (mayor) "Vino să-mi iei tronul, dacă poți." else "Tu ai fost? Fă check-in și tu.",
            W / 2f, after + 80f, text(44f, LILAC, bold = false).apply { textAlign = Paint.Align.CENTER },
        )

        // jos: invitație + link
        val pill = RectF(140f, H - 230f, W - 140f, H - 120f)
        c.drawRoundRect(pill, 60f, 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD })
        c.drawText("Descarcă CityConnect Arad", W / 2f, pill.centerY() + 18f,
            text(48f, NIGHT, bold = true).apply { textAlign = Paint.Align.CENTER })
        c.drawText("Hack4Arad 2026 · Promovează Județul Arad", W / 2f, H - 60f,
            text(34f, 0xFF7A80A8.toInt(), bold = false).apply { textAlign = Paint.Align.CENTER })

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "topul-aradului-${place.id}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(1200).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        return (result.drawable as? BitmapDrawable)?.bitmap
    }

    private fun text(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
}
