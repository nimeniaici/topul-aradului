package ro.hack4arad.topul.data

// Câmpurile sunt în camelCase; Gson le traduce automat din snake_case (vezi Network.kt).

/** name = username-ul unic (ex. diana.arad) */
data class RegisterIn(val name: String, val email: String, val password: String)
data class UsernameCheck(val username: String, val available: Boolean, val reason: String?, val suggestion: String? = null)
data class LoginIn(val email: String, val password: String)

data class UserOut(val id: Int, val name: String, val xp: Int, val level: Int, val avatarEmoji: String?, val avatarColor: String?, val avatarUrl: String? = null)
data class GoogleIn(val idToken: String)
data class ForgotIn(val email: String)
data class ResetIn(val email: String, val code: String, val newPassword: String)
data class MessageOut(val detail: String)
/** Doar câmpurile trimise (ne-null) se schimbă pe server. */
data class UpdateMeIn(val name: String? = null, val avatarEmoji: String? = null, val avatarColor: String? = null)
data class TokenOut(val accessToken: String, val user: UserOut)

data class Category(val code: String, val label: String, val count: Int)

data class Place(
    val id: Int,
    val name: String,
    val category: String,
    val categoryLabel: String,
    val description: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String?,
    val splatUrl: String?,
    val rating: Double,
    val duels: Int,
    val wins: Int,
    val checkinCount: Int,
    val mayorId: Int?,
    val mayorName: String?,
    val verified: Boolean,
    val distanceM: Double?,
)

data class Photo(val photoUrl: String, val userName: String, val createdAt: String)

data class PlaceDetail(
    val id: Int,
    val name: String,
    val category: String,
    val categoryLabel: String,
    val description: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String?,
    val splatUrl: String?,
    val rating: Double,
    val duels: Int,
    val wins: Int,
    val checkinCount: Int,
    val mayorId: Int?,
    val mayorName: String?,
    val verified: Boolean,
    val recentPhotos: List<Photo>,
    val rankInCategory: Int,
    val splatMb: Double?,
    val story: String? = null,
    val oldPhoto: OldPhoto? = null,
)

/** Poză veche (liberă de drepturi) pentru „Trecut vs prezent”. */
data class OldPhoto(val url: String, val year: String, val credit: String)

data class Badge(val code: String, val title: String, val description: String, val earnedAt: String) {
    /** Serverul trimite 1970-01-01 pentru insignele încă neobținute. */
    val earned: Boolean get() = !earnedAt.startsWith("1970")
}

data class CheckinResult(
    val checkinId: Int,
    val placeId: Int,
    val xpGained: Int,
    val totalXp: Int,
    val level: Int,
    val newBadges: List<Badge>,
    val becameMayor: Boolean,
    val distanceM: Double,
)

data class HeatPoint(val lat: Double, val lng: Double, val placeId: Int, val createdAt: String)

data class Duel(val category: String, val categoryLabel: String, val a: Place, val b: Place)
data class VoteIn(val winnerId: Int, val loserId: Int)
data class VoteOut(val winner: Place, val loser: Place, val xpGained: Int, val newBadges: List<Badge>)

data class UserRank(
    val id: Int, val name: String, val xp: Int, val level: Int, val rank: Int,
    val avatarEmoji: String?, val avatarColor: String?, val avatarUrl: String? = null,
)

data class Profile(
    val id: Int,
    val name: String,
    val email: String,
    val avatarEmoji: String?,
    val avatarColor: String?,
    val avatarUrl: String? = null,
    val hasPassword: Boolean,
    val googleLinked: Boolean,
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpPerLevel: Int,
    val checkins: Int,
    val placesVisited: Int,
    val votes: Int,
    val badges: List<Badge>,
    val allBadges: List<Badge>,
    val mayorOf: List<Place>,
    val missions: List<Mission>,
)

/** O insignă încă neobținută, văzută ca misiune: ce ai de făcut și cât ai progresat. */
data class Mission(val code: String, val title: String, val reward: String, val progress: Int, val target: Int)

/** Mesaje primite live prin WebSocket (/live). */
data class LiveEvent(
    val type: String, // "checkin" sau "mayor"
    val lat: Double?,
    val lng: Double?,
    val placeId: Int?,
    val placeName: String?,
    val userName: String?,
    val oldMayorId: Int?,
    val newMayorId: Int?,
    val newMayorName: String?,
    // pentru „report_status” (Primăria a schimbat statusul unei sesizări)
    val userId: Int? = null,
    val roomId: Int? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val xpGained: Int? = null,
    val note: String? = null,
)

/** Sesizare de la cetățeni (groapă, iluminat...). Poziția e a problemei. */
data class Report(
    val id: Int,
    val category: String,
    val categoryLabel: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String?,
    val status: String,
    val userName: String,
    val createdAt: String,
    val statusLabel: String? = null,
    val adminNote: String? = null,
    val urgent: Boolean = false,
    val updatedAt: String? = null,
)

/** Un loc din „Descoperă Aradul”: are ghid audio, poză veche sau tur 3D. */
data class DiscoverPlace(
    val id: Int,
    val name: String,
    val category: String,
    val categoryLabel: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String?,
    val hasStory: Boolean,
    @com.google.gson.annotations.SerializedName("has_3d") val has3d: Boolean,
    val oldPhoto: OldPhoto?,
)

data class ReportCreated(val report: Report, val xpGained: Int, val totalXp: Int, val level: Int)

/** O vizită din jurnalul meu (check-in). */
data class MyCheckin(val id: Int, val placeId: Int, val placeName: String, val photoUrl: String?, val createdAt: String)


data class PasswordIn(val oldPassword: String, val newPassword: String)
data class MyStats(val rank: Int, val totalUsers: Int, val reports: Int, val reportsResolved: Int, val memberSince: String)
data class FeedbackIn(val kind: String, val message: String, val appVersion: String, val device: String)

/** Chat cu Iasmi (asistentul AI). role = "user" sau "assistant". */
data class IasmiMessage(val role: String, val content: String)
data class IasmiIn(val messages: List<IasmiMessage>, val lat: Double? = null, val lng: Double? = null)
data class IasmiPlace(val id: Int, val name: String, val category: String, val address: String, val distanceM: Double? = null)
data class IasmiOut(val reply: String, val ai: Boolean, val places: List<IasmiPlace> = emptyList())

// ---------- Transport CTP ----------
data class TransitStopRef(val id: String, val name: String, val lat: Double, val lng: Double, val minFromStart: Int = 0)
data class TransitLine(
    val ref: String, val name: String, val from: String, val to: String, val color: String,
    val stops: List<TransitStopRef>, val shape: List<List<Double>>,
    val headwayPeak: Int, val headwayOffpeak: Int, val headwayWeekend: Int, val first: String, val last: String,
    val id: Int = 0, val mode: String = "tram", val shapes: List<List<List<Double>>>? = null,
)
data class TicketType(val code: String, val label: String, val price: Double, val minutes: Int)
data class TransitInfo(val tickets: List<TicketType>, val dispatchPhone: String, val source: String, val note: String)
data class TransitStop(val id: String, val name: String, val lat: Double, val lng: Double, val lines: List<String>, val distanceM: Int? = null)
data class Departure(val line: String, val color: String, val towards: String, val minutes: List<Int>, val crowded: Int, val delayed: Int,
                     val mode: String = "tram", val live: Boolean = false)
data class NextTrams(val stop: TransitStop, val departures: List<Departure>, val available: Boolean = true)
data class Vehicle(val id: String, val line: String, val mode: String, val color: String, val lat: Double, val lng: Double,
                   val heading: Double?, val towards: String, val nextStop: String?, val delayMin: Int, val updated: String)
data class VehiclesOut(val vehicles: List<Vehicle>, val available: Boolean, val source: String)

// ---------- calendar gunoi (RETIM) ----------
data class GunoiPlace(val id: Int, val street: String, val cartier: String)
data class GunoiFraction(val type: String, val label: String, val hint: String, val color: String, val next: String,
                         val nextLabel: String, val inDays: Int, val dates: List<String>, val weekday: String?)
data class GunoiUpcoming(val date: String, val label: String, val type: String, val typeLabel: String, val color: String)
data class GunoiSchedule(val place: GunoiPlace, val fractions: List<GunoiFraction>, val upcoming: List<GunoiUpcoming>,
                         val validUntil: String, val source: String, val note: String)

// ---------- turism (CNIPT Arad) ----------
data class TurismType(val code: String, val label: String)
data class TurismLoc(val id: Int, val name: String, val type: String, val category: String, val locality: String, val address: String,
                     val lat: Double, val lng: Double, val photo: String?, val summary: String, val distanceM: Int?)
data class TurismList(val locations: List<TurismLoc>, val types: List<TurismType>, val source: String)
data class TurismDetail(val id: Int, val name: String, val type: String, val category: String, val locality: String, val address: String,
                        val lat: Double, val lng: Double, val description: String, val website: String, val facebook: String,
                        val photos: List<String>, val sourceUrl: String, val source: String)
data class Trail(val id: Int, val name: String, val category: String, val description: String, val mapImageUrl: String,
                 val photos: List<String>, val sourceUrl: String)
data class TrailsOut(val trails: List<Trail>, val source: String)
data class TransitReportIn(val line: String, val kind: String, val stopId: String? = null, val note: String = "")
data class TransitReportOut(val line: String, val kind: String, val label: String, val stopName: String?, val note: String, val createdAt: String)
data class TicketIn(val type: String)
data class Ticket(val id: Int, val type: String, val label: String, val price: Double, val code: String,
                  val validFrom: String, val validUntil: String, val active: Boolean, val demo: Boolean)

// ---------- prieteni ----------
data class UserCard(val id: Int, val name: String, val level: Int, val xp: Int, val avatarEmoji: String?, val avatarColor: String?,
                    val avatarUrl: String?, val thrones: Int)
data class FriendsOut(val friends: List<UserCard>, val incoming: List<UserCard>, val outgoing: List<UserCard>,
                      val blocked: List<UserCard>, val sharing: Boolean)
data class UsernameIn(val username: String)
data class SharingIn(val sharing: Boolean)
data class LatLngIn(val lat: Double, val lng: Double)
data class ConqueredPlace(val id: Int, val name: String, val category: String, val lat: Double, val lng: Double)
data class FriendProfile(val id: Int, val name: String, val level: Int, val xp: Int, val avatarEmoji: String?, val avatarColor: String?,
                         val avatarUrl: String?, val thrones: Int, val conquered: List<ConqueredPlace>)
data class FriendLocation(val id: Int, val name: String, val avatarUrl: String?, val avatarColor: String?, val avatarEmoji: String?,
                          val lat: Double, val lng: Double, val updatedAt: String, val thrones: Int, val level: Int)

// ---------- comunitate ----------
data class RoomTag(val code: String, val label: String)
data class Room(val id: Int, val kind: String, val tag: String, val tagLabel: String, val name: String, val description: String,
                val members: Int?, val inviteCode: String?, val isOwner: Boolean, val lastText: String?, val lastAt: String?,
                val avatarUrl: String?)
data class RoomsOut(val rooms: List<Room>, val tags: List<RoomTag>)
data class RoomIn(val name: String, val tag: String, val description: String = "")
data class CodeIn(val code: String)
data class ChatMsg(val id: Int, val kind: String, val text: String, val userId: Int, val userName: String, val avatarUrl: String?,
                   val avatarColor: String?, val mine: Boolean, val eventTitle: String?, val eventAt: String?, val eventPlace: String?,
                   val createdAt: String)
data class ChatMsgIn(val text: String, val kind: String = "text", val eventTitle: String? = null, val eventAt: String? = null,
                     val eventPlace: String? = null, val eventLat: Double? = null, val eventLng: Double? = null)
data class CityEvent(val id: Int, val title: String, val text: String, val at: String, val place: String?, val lat: Double?,
                     val lng: Double?, val by: String)
