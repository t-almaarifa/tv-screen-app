package sa.org.talmaarifa.tvsignage.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url
import sa.org.talmaarifa.tvsignage.network.dto.EventsRequestDto
import sa.org.talmaarifa.tvsignage.network.dto.EventsResponseDto
import sa.org.talmaarifa.tvsignage.network.dto.HeartbeatRequestDto
import sa.org.talmaarifa.tvsignage.network.dto.HeartbeatResponseDto
import sa.org.talmaarifa.tvsignage.network.dto.ManifestResponseDto
import sa.org.talmaarifa.tvsignage.network.dto.PairRequestResponseDto
import sa.org.talmaarifa.tvsignage.network.dto.PairStatusRequestDto
import sa.org.talmaarifa.tvsignage.network.dto.PairStatusResponseDto

/**
 * Unauthenticated pairing endpoints — no credential exists yet (docs/tv-device-api.md §1-2).
 * Built on an OkHttpClient with no [AuthInterceptor].
 */
interface PairingApi {
    @POST("api/v1/tv/device/pair/request")
    suspend fun requestPairing(): PairRequestResponseDto

    @POST("api/v1/tv/device/pair/status")
    suspend fun pairingStatus(@Body body: PairStatusRequestDto): PairStatusResponseDto
}

/**
 * Authenticated device endpoints (docs/tv-device-api.md §3-6). Built on an OkHttpClient whose
 * [AuthInterceptor] attaches `Authorization: Bearer tv_<device_id>.<secret>` to every request.
 */
interface DeviceApi {
    @POST("api/v1/tv/device/heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatRequestDto): HeartbeatResponseDto

    @GET("api/v1/tv/device/manifest")
    suspend fun manifest(): ManifestResponseDto

    /**
     * [path] is the exact `download_url` the manifest returned (e.g.
     * "/api/v1/tv/device/media/<id>/download") — an absolute path, no signature, re-authorized by
     * the Bearer credential on every call (docs/tv-device-api.md §5).
     */
    @Streaming
    @GET
    suspend fun downloadMedia(@Url path: String): Response<ResponseBody>

    @POST("api/v1/tv/device/events")
    suspend fun uploadEvents(@Body body: EventsRequestDto): EventsResponseDto
}
