package ro.hack4arad.topul.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ro.hack4arad.topul.util.Fix

/** Check-in folosit și de pe hartă, și din pagina locului. */
object Checkins {
    private val text = "text/plain".toMediaType()

    suspend fun checkIn(placeId: Int, fix: Fix, photo: ByteArray? = null): CheckinResult {
        val photoPart = photo?.let {
            MultipartBody.Part.createFormData("photo", "checkin.jpg", it.toRequestBody("image/jpeg".toMediaType()))
        }
        return Network.api.checkin(
            placeId = placeId.toString().toRequestBody(text),
            lat = fix.lat.toString().toRequestBody(text),
            lng = fix.lng.toString().toRequestBody(text),
            isMock = fix.isMock.toString().toRequestBody(text),
            photo = photoPart,
        )
    }
}
