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
import retrofit2.http.PUT
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

    @GET("reports/mine")
    suspend fun myReports(): List<Report>

    @POST("reports/{id}/confirm")
    suspend fun confirmReport(@Path("id") id: Int, @Body body: ConfirmIn): ReportCreated

    @GET("places/discover")
    suspend fun discover(): List<DiscoverPlace>

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

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part photo: MultipartBody.Part): Profile

    @DELETE("users/me/avatar")
    suspend fun deleteAvatar(): Profile

    @POST("users/me/password")
    suspend fun changePassword(@Body body: PasswordIn): MessageOut

    @GET("users/me/stats")
    suspend fun myStats(): MyStats

    @POST("feedback")
    suspend fun sendFeedback(@Body body: FeedbackIn): MessageOut

    @POST("iasmi")
    suspend fun iasmi(@Body body: IasmiIn): IasmiOut

    @GET("transit/lines")
    suspend fun transitLines(): List<TransitLine>

    @GET("transit/info")
    suspend fun transitInfo(): TransitInfo

    @GET("transit/stops/near")
    suspend fun nearStops(@Query("lat") lat: Double, @Query("lng") lng: Double, @Query("limit") limit: Int = 6): List<TransitStop>

    @GET("transit/stops/{id}/next")
    suspend fun nextTrams(@Path("id") stopId: String): NextTrams

    @GET("users/{id}/public")
    suspend fun publicProfile(@Path("id") id: Int): PublicProfile

    @GET("transit/stops/{id}/timetable")
    suspend fun timetable(@Path("id") stopId: String): Timetable

    @POST("transit/checkin")
    suspend fun stopCheckin(@Body body: StopCheckinIn): StopCheckinOut

    @GET("me/rewards")
    suspend fun rewards(): RewardsOut

    @GET("transit/vehicles")
    suspend fun vehicles(): VehiclesOut

    @GET("gunoi/search")
    suspend fun gunoiSearch(@Query("q") q: String): List<GunoiPlace>

    @GET("gunoi/{id}")
    suspend fun gunoi(@Path("id") id: Int): GunoiSchedule

    @GET("turism")
    suspend fun turism(
        @Query("type") type: String? = null, @Query("q") q: String? = null, @Query("only_arad") onlyArad: Boolean = false,
        @Query("lat") lat: Double? = null, @Query("lng") lng: Double? = null,
    ): TurismList

    @GET("turism/{id}")
    suspend fun turismDetail(@Path("id") id: Int): TurismDetail

    @GET("turism/trasee")
    suspend fun trails(): TrailsOut

    @POST("transit/reports")
    suspend fun transitReport(@Body body: TransitReportIn): MessageOut

    @GET("transit/reports")
    suspend fun transitReports(@Query("minutes") minutes: Int = 60): List<TransitReportOut>

    @POST("transit/tickets")
    suspend fun buyTicket(@Body body: TicketIn): Ticket

    @GET("transit/tickets/mine")
    suspend fun myTickets(): List<Ticket>

    // ---------- prieteni ----------
    @GET("friends")
    suspend fun friends(): FriendsOut

    @GET("users/search")
    suspend fun searchUsers(@Query("q") q: String): List<UserCard>

    @POST("friends/request")
    suspend fun friendRequest(@Body body: UsernameIn): MessageOut

    @POST("friends/{id}/accept")
    suspend fun acceptFriend(@Path("id") id: Int): MessageOut

    @DELETE("friends/{id}")
    suspend fun removeFriend(@Path("id") id: Int): MessageOut

    @POST("friends/{id}/block")
    suspend fun blockUser(@Path("id") id: Int): MessageOut

    @POST("friends/{id}/unblock")
    suspend fun unblockUser(@Path("id") id: Int): MessageOut

    @GET("friends/{id}/profile")
    suspend fun friendProfile(@Path("id") id: Int): FriendProfile

    @PUT("me/sharing")
    suspend fun setSharing(@Body body: SharingIn): SharingIn

    @PUT("me/location")
    suspend fun putLocation(@Body body: LatLngIn): Response<Unit>

    @GET("friends/locations")
    suspend fun friendLocations(): List<FriendLocation>

    // ---------- comunitate ----------
    @GET("community/rooms")
    suspend fun rooms(): RoomsOut

    @POST("community/rooms")
    suspend fun createRoom(@Body body: RoomIn): Room

    @POST("community/rooms/join")
    suspend fun joinRoom(@Body body: CodeIn): Room

    @POST("community/rooms/{id}/members")
    suspend fun addMember(@Path("id") id: Int, @Body body: UsernameIn): MessageOut

    @POST("community/rooms/{id}/leave")
    suspend fun leaveRoom(@Path("id") id: Int): MessageOut

    @POST("community/direct/{id}")
    suspend fun directRoom(@Path("id") friendId: Int): Room

    @GET("community/rooms/{id}/messages")
    suspend fun roomMessages(@Path("id") id: Int, @Query("after_id") afterId: Int = 0): List<ChatMsg>

    @POST("community/rooms/{id}/messages")
    suspend fun sendMessage(@Path("id") id: Int, @Body body: ChatMsgIn): ChatMsg

    @GET("community/events")
    suspend fun events(): List<CityEvent>

    @GET("polls")
    suspend fun polls(): List<Poll>

    @POST("polls")
    suspend fun createPoll(@Body body: PollIn): Poll

    @POST("polls/{id}/vote")
    suspend fun vote(@Path("id") id: Int, @Body body: PollVoteIn): Poll

    @retrofit2.http.DELETE("polls/{id}")
    suspend fun deletePoll(@Path("id") id: Int): retrofit2.Response<Unit>

    @GET("servicii")
    suspend fun servicii(@Query("type") type: String? = null, @Query("lat") lat: Double? = null, @Query("lng") lng: Double? = null,
                         @Query("limit") limit: Int = 120): ServiciiOut
}
