package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.LiveSessionResponse
import com.tukorea.synclab_mobile.data.model.LiveStatusResponse
import com.tukorea.synclab_mobile.data.model.LiveTokenResponse
import com.tukorea.synclab_mobile.data.model.OverlayData
import com.tukorea.synclab_mobile.data.model.SwitchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface LiveApiService {

    @POST("api/live/session/create")
    suspend fun createLiveSession(
        @Body body: Map<String, String>
    ): Response<LiveSessionResponse>

    @POST("api/live/session/token")
    suspend fun getLiveToken(
        @Body body: Map<String, String>
    ): Response<LiveTokenResponse>

    @POST("api/live/session/switch")
    suspend fun switchCamera(
        @Body body: Map<String, String>
    ): Response<SwitchResponse>

    @POST("api/live/session/go-live")
    suspend fun goLive(
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("api/live/session/end-live")
    suspend fun endLive(
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @GET("api/live/session/{sessionId}/status")
    suspend fun getLiveStatus(
        @Path("sessionId") sessionId: String
    ): Response<LiveStatusResponse>

    @GET("api/live/session/{sessionId}/overlay")
    suspend fun getOverlay(
        @Path("sessionId") sessionId: String
    ): Response<OverlayData>

    @POST("api/live/session/{sessionId}/overlay")
    suspend fun updateOverlay(
        @Path("sessionId") sessionId: String,
        @Body body: OverlayData
    ): Response<Map<String, String>>
}
