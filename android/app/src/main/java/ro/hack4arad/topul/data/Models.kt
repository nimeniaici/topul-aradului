package ro.hack4arad.topul.data

// Câmpurile sunt în camelCase; Gson le traduce automat din snake_case (vezi Network.kt).

data class RegisterIn(val name: String, val email: String, val password: String)
data class LoginIn(val email: String, val password: String)

data class UserOut(val id: Int, val name: String, val xp: Int, val level: Int, val avatarEmoji: String?, val avatarColor: String?)
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
    val rating: Double,
    val duels: Int,
    val wins: Int,
    val checkinCount: Int,
    val mayorId: Int?,
    val mayorName: String?,
    val verified: Boolean,
    val recentPhotos: List<Photo>,
    val rankInCategory: Int,
)

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
    val avatarEmoji: String?, val avatarColor: String?,
)

data class Profile(
    val id: Int,
    val name: String,
    val email: String,
    val avatarEmoji: String?,
    val avatarColor: String?,
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
)
