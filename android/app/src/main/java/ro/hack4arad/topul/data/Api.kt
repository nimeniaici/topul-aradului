package ro.hack4arad.topul.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Toate endpoint-urile backend-ului FastAPI (vezi http://localhost:8000/docs). */
interface Api {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterIn): TokenOut

    @GET("auth/username-available")
    suspend fun usernameAvailable(@Query("u") username: String): UsernameCheck

    @POST("auth/login")
    suspend fun login(@Body body: LoginIn): TokenOut

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleIn): TokenOut

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotIn): MessageOut

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetIn): TokenOut

    @GET("places/categories")
    suspend fun categories(): List<Category>

    @GET("places")
    suspend fun places(
        @Query("category") category: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius_km") radiusKm: Double? = null,
    ): List<Place>

    @GET("places/{id}")
    suspend fun place(@Path("id") id: Int): PlaceDetail

    @Multipart
    @POST("checkins")
    suspend fun checkin(
        @Part("place_id") placeId: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("is_mock") isMock: RequestBody,
        @Part photo: MultipartBody.Part?, // null = fără poză
    ): CheckinResult

    @GET("checkins/recent")
    suspend fun recentCheckins(@Query("minutes") minutes: Int = 60): List<HeatPoint>

    @GET("duels/categories")
    suspend fun duelCategories(): List<Category>

    @GET("duels/next")
    suspend fun nextDuel(@Query("category") category: String? = null): Duel

    @POST("duels/vote")
    suspend fun vote(@Body body: VoteIn): VoteOut

    @GET("leaderboard/places")
    suspend fun topPlaces(
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): List<Place>

    @GET("leaderboard/users")
    suspend fun topUsers(@Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0): List<UserRank>

    @GET("users/me")
    suspend fun me(): Profile

    @GET("checkins/mine")
    suspend fun myCheckins(): List<MyCheckin>

    @GET("reports")
    suspend fun reports(@Query("days") days: Int = 30): List<Report>

    @Multipart
    @POST("reports")
    suspend fun createReport(
        @Part("category") category: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("description") description: RequestBody,
        @Part("is_mock") isMock: RequestBody,
        @Part photo: MultipartBody.Part?,
    ): ReportCreated

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateMeIn): Profile

    @DELETE("users/me")
    suspend fun deleteMe(): Response<Unit>
}
