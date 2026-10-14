package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Health
    @GET("health")
    suspend fun checkHealth(): HealthResponse

    // Auth
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): LoginResponse

    @POST("auth/refresh")
    suspend fun refresh(): RefreshResponse

    @GET("auth/me")
    suspend fun getMe(): User

    @GET("auth/me/robots")
    suspend fun getMyRobots(): List<RobotStatus>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    // Robot Control & Telemetry
    @GET("robot/status")
    suspend fun getRobotStatus(): RobotStatus

    @POST("robot/mode")
    suspend fun setRobotMode(@Body body: ModeRequest): RobotStatus

    @POST("robot/emergency-stop")
    suspend fun emergencyStop(@Body body: EmergencyStopRequest = EmergencyStopRequest()): SafetyStateResponse

    @POST("robot/emergency-stop/reset")
    suspend fun resetEmergencyStop(): SafetyStateResponse

    @GET("robot/safety-state")
    suspend fun getSafetyState(): SafetyStateResponse

    // Mapping Sessions
    @GET("mapping/sessions")
    suspend fun getMappingSessions(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<MappingSession>

    @POST("mapping/sessions")
    suspend fun createMappingSession(@Body body: CreateSessionRequest): MappingSession

    @GET("mapping/sessions/{id}")
    suspend fun getMappingSession(@Path("id") id: String): MappingSession

    @POST("mapping/sessions/{id}/start")
    suspend fun startMappingSession(@Path("id") id: String): MappingSession

    @POST("mapping/sessions/{id}/pause")
    suspend fun pauseMappingSession(@Path("id") id: String): MappingSession

    @POST("mapping/sessions/{id}/resume")
    suspend fun resumeMappingSession(@Path("id") id: String): MappingSession

    @POST("mapping/sessions/{id}/finish")
    suspend fun finishMappingSession(
        @Path("id") id: String,
        @Body body: FinishSessionRequest = FinishSessionRequest()
    ): MappingSession

    // Destinations
    @GET("destinations")
    suspend fun getDestinations(
        @Query("map_id") mapId: String? = null
    ): List<Destination>

    @POST("destinations")
    suspend fun createDestination(@Body body: CreateDestinationRequest): Destination

    @PATCH("destinations/{id}")
    suspend fun updateDestination(
        @Path("id") id: String,
        @Body body: UpdateDestinationRequest
    ): Destination

    @DELETE("destinations/{id}")
    suspend fun deleteDestination(@Path("id") id: String): Response<Unit>

    // Deliveries
    @GET("deliveries")
    suspend fun getDeliveries(): List<Delivery>

    @GET("deliveries/mine")
    suspend fun getMyDeliveries(): List<Delivery>

    @POST("deliveries")
    suspend fun createDelivery(@Body body: CreateDeliveryRequest): Delivery

    @GET("deliveries/{id}")
    suspend fun getDelivery(@Path("id") id: String): Delivery

    @POST("deliveries/{id}/dispatch")
    suspend fun dispatchDelivery(@Path("id") id: String): Delivery

    @POST("deliveries/{id}/confirm-pickup")
    suspend fun confirmPickupDelivery(@Path("id") id: String): Delivery

    @POST("deliveries/{id}/cancel")
    suspend fun cancelDelivery(@Path("id") id: String): Delivery

    // Events (Audit log)
    @GET("events")
    suspend fun getEvents(
        @Query("severity") severity: String? = null,
        @Query("type") type: String? = null,
        @Query("robot_id") robotId: String? = null,
        @Query("user_id") userId: String? = null,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50
    ): EventsResponse

    // Users (admin)
    @GET("admin/users")
    suspend fun getUsers(): List<User>

    @POST("admin/users")
    suspend fun createUser(@Body body: CreateUserRequest): User

    @PATCH("admin/users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body body: UpdateUserRequest
    ): User

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    @POST("admin/users/{user_id}/robots/{robot_id}")
    suspend fun grantRobotAccess(
        @Path("user_id") userId: String,
        @Path("robot_id") robotId: String
    ): Response<Unit>

    @DELETE("admin/users/{user_id}/robots/{robot_id}")
    suspend fun revokeRobotAccess(
        @Path("user_id") userId: String,
        @Path("robot_id") robotId: String
    ): Response<Unit>

    @GET("admin/users/{user_id}/robots")
    suspend fun getUserRobots(@Path("user_id") userId: String): List<RobotStatus>
}
