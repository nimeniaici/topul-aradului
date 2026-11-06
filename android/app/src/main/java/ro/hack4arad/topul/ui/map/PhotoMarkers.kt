package ro.hack4arad.topul.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.PlacePhotos
import ro.hack4arad.topul.ui.components.categoryColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** Un loc pe hartă, în formatul cerut de gruparea markerelor. */
class PlaceItem(val place: Place) : ClusterItem {
    override fun getPosition() = LatLng(place.lat, place.lng)
    override fun getTitle() = place.name
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = if (place.mayorId == Network.session.userId) 2f else 1f
}

/**
 * Markerele sunt desenate O SINGURĂ DATĂ ca imagini și apoi doar mutate de hartă (de-asta harta e fluidă):
 * poza rotundă a locului cu inel în culoarea categoriei; grupurile = poza primului loc + numărul.
 */
object MarkerArt {
    private val photos = ConcurrentHashMap<Int, Bitmap>()
    private val descriptors = ConcurrentHashMap<String, BitmapDescriptor>()
    private const val GOLD = 0xFFFFD23F.toInt()
    private const val NIGHT = 0xFF0F1226.toInt()

    fun photo(id: Int): Bitmap? = photos[id]
    fun putPhoto(id: Int, bmp: Bitmap) { photos[id] = bmp }

    fun place(context: Context, p: Place, highlighted: Boolean = false): BitmapDescriptor {
        val mine = p.mayorId != null && p.mayorId == Network.session.userId
        val photo = photos[p.id]
        val key = "p${p.id}|${photo != null}|$mine|${p.mayorId == null}|$highlighted"
        return descriptors.getOrPut(key) {
            val d = context.resources.displayMetrics.density
            val size = ((if (highlighted) 56 else 44) * d).toInt()
            val pad = (4 * d).toInt()
            val bmp = Bitmap.createBitmap(size + pad * 2, size + pad * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val ring = if (mine) GOLD else categoryColor(p.category).toArgb()
            drawPhotoCircle(c, pad.toFloat(), size.toFloat(), photo, ring, (3 * d), p.name, d)
            if (p.mayorId == null && !mine) {
                // bulina aurie = „tron liber”
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val r = 6 * d
                val cx = pad + size - r * 0.6f
                val cy = pad + r * 0.9f
                paint.color = NIGHT; c.drawCircle(cx, cy, r + 2 * d, paint)
                paint.color = GOLD; c.drawCircle(cx, cy, r, paint)
            }
            BitmapDescriptorFactory.fromBitmap(bmp)
        }
    }

    fun cluster(context: Context, count: Int, cover: Place?): BitmapDescriptor {
        val photo = cover?.let { photos[it.id] }
        val key = "c$count|${cover?.id}|${photo != null}"
        return descriptors.getOrPut(key) {
            val d = context.resources.displayMetrics.density
            val size = (50 * d).toInt()
            val pad = (8 * d).toInt()
            val bmp = Bitmap.createBitmap(size + pad * 2, size + pad * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawPhotoCircle(c, pad.toFloat(), size.toFloat(), photo, GOLD, 3 * d, cover?.name ?: "", d, darken = true)
            // numărul, într-o pastilă violet în colț
            val text = "+$count"
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); textSize = 12 * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val w = tp.measureText(text) + 12 * d
            val h = 20 * d
            val rect = RectF(bmp.width - w - 1 * d, 1 * d, bmp.width - 1 * d, 1 * d + h)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = NIGHT; c.drawRoundRect(RectF(rect.left - 2 * d, rect.top - 2 * d, rect.right + 2 * d, rect.bottom + 2 * d), h, h, bg)
            bg.color = 0xFF7C5CFF.toInt(); c.drawRoundRect(rect, h, h, bg)
            c.drawText(text, rect.centerX(), rect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
            BitmapDescriptorFactory.fromBitmap(bmp)
        }
    }

    private fun drawPhotoCircle(
        c: Canvas, offset: Float, size: Float, photo: Bitmap?, ring: Int, ringW: Float, name: String, d: Float,
        darken: Boolean = false,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = offset + size / 2
        val cy = offset + size / 2
        val r = size / 2
        paint.color = NIGHT; c.drawCircle(cx, cy, r + 1.5f * d, paint)       // contur subțire închis
        paint.color = ring; c.drawCircle(cx, cy, r, paint)                     // inelul colorat
        val inner = r - ringW
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (inner * 2) / min(photo.width, photo.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
            }
            shader.setLocalMatrix(m)
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            c.drawCircle(cx, cy, inner, pp)
            if (darken) { paint.color = 0x55000000; c.drawCircle(cx, cy, inner, paint) }
        } else {
            // fără poză încă: inițiala locului pe fundal închis, în culoarea inelului
            paint.color = NIGHT; c.drawCircle(cx, cy, inner, paint)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ring; textSize = inner * 0.95f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
            c.drawText(letter, cx, cy - (tp.descent() + tp.ascent()) / 2, tp)
        }
    }

    /** Descarcă poza locului (lazy), o micșorează pentru marker și o ține minte. */
    suspend fun loadPhoto(context: Context, p: Place): Boolean {
        if (photos.containsKey(p.id)) return true
        val url = Network.fullUrl(p.photoUrl) ?: PlacePhotos.url(p.id, p.name, p.lat, p.lng) ?: return false
        val req = ImageRequest.Builder(context).data(url).size(160).allowHardware(false).build()
        val bmp = (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap ?: return false
        photos[p.id] = bmp
        return true
    }
}

/** Desenează markerele cu poze și fără animațiile lente de grupare. */
class PhotoClusterRenderer(
    private val context: Context,
    map: GoogleMap,
    manager: ClusterManager<PlaceItem>,
    private val scope: CoroutineScope,
) : DefaultClusterRenderer<PlaceItem>(context, map, manager) {

    var highlightedId: Int? = null

    init {
        minClusterSize = 2
        setAnimation(false)
    }

    override fun onBeforeClusterItemRendered(item: PlaceItem, markerOptions: MarkerOptions) {
        markerOptions.icon(MarkerArt.place(context, item.place, item.place.id == highlightedId))
            .anchor(0.5f, 0.5f).title(null)
    }

    override fun onClusterItemUpdated(item: PlaceItem, marker: Marker) {
        marker.setIcon(MarkerArt.place(context, item.place, item.place.id == highlightedId))
    }

    override fun onClusterItemRendered(item: PlaceItem, marker: Marker) {
        if (MarkerArt.photo(item.place.id) != null) return
        scope.launch {
            if (MarkerArt.loadPhoto(context, item.place)) {
                withContext(Dispatchers.Main) {
                    getMarker(item)?.setIcon(MarkerArt.place(context, item.place, item.place.id == highlightedId))
                }
            }
        }
    }

    private fun cover(cluster: Cluster<PlaceItem>): Place? =
        cluster.items.firstOrNull { MarkerArt.photo(it.place.id) != null }?.place ?: cluster.items.firstOrNull()?.place

    override fun onBeforeClusterRendered(cluster: Cluster<PlaceItem>, markerOptions: MarkerOptions) {
        markerOptions.icon(MarkerArt.cluster(context, cluster.size, cover(cluster))).anchor(0.5f, 0.5f)
    }

    override fun onClusterUpdated(cluster: Cluster<PlaceItem>, marker: Marker) {
        marker.setIcon(MarkerArt.cluster(context, cluster.size, cover(cluster)))
    }

    override fun onClusterRendered(cluster: Cluster<PlaceItem>, marker: Marker) {
        val first = cluster.items.firstOrNull()?.place ?: return
        if (MarkerArt.photo(first.id) != null) return
        scope.launch {
            if (MarkerArt.loadPhoto(context, first)) {
                withContext(Dispatchers.Main) { getMarker(cluster)?.setIcon(MarkerArt.cluster(context, cluster.size, cover(cluster))) }
            }
        }
    }

    /** Evidențiază locul din carusel (marker mai mare). */
    fun highlight(id: Int?, items: Collection<PlaceItem>) {
        val old = highlightedId
        highlightedId = id
        items.filter { it.place.id == old || it.place.id == id }.forEach { item ->
            getMarker(item)?.setIcon(MarkerArt.place(context, item.place, item.place.id == id))
            getMarker(item)?.zIndex = if (item.place.id == id) 5f else item.zIndex
        }
    }
}
