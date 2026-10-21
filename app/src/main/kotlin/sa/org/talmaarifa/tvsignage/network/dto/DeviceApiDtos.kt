package sa.org.talmaarifa.tvsignage.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire shapes for `/api/v1/tv/device/*`, field-for-field against docs/tv-device-api.md (itself
 * extracted from the live route handlers — src/app/api/v1/tv/device/*). Do not rename or add fields
 * here without re-checking that document: the server rejects unknown properties with 422
 * VALIDATION_FAILED on every write endpoint.
 */

// ---------------------------------------------------------------- 1) pair/request

@JsonClass(generateAdapter = true)
data class PairRequestResponseDto(
    @Json(name = "code") val code: String,
    @Json(name = "poll_token") val pollToken: String,
    @Json(name = "expires_at") val expiresAt: String,
)

// ---------------------------------------------------------------- 2) pair/status

@JsonClass(generateAdapter = true)
data class PairStatusRequestDto(
    @Json(name = "poll_token") val pollToken: String,
)

@JsonClass(generateAdapter = true)
data class PairStatusResponseDto(
    @Json(name = "status") val status: String, // "pending" | "approved" | "rejected" | "expired"
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "credential") val credential: String? = null,
)

// ---------------------------------------------------------------- 3) heartbeat

@JsonClass(generateAdapter = true)
data class HeartbeatRequestDto(
    @Json(name = "app_version") val appVersion: String?,
    @Json(name = "current_publication_id") val currentPublicationId: String?,
    @Json(name = "storage_free_bytes") val storageFreeBytes: Long?,
    @Json(name = "last_error") val lastError: String?,
)

@JsonClass(generateAdapter = true)
data class HeartbeatResponseDto(
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "poll_interval_seconds") val pollIntervalSeconds: Int,
    @Json(name = "publication_changed") val publicationChanged: Boolean,
)

// ---------------------------------------------------------------- 4) manifest

@JsonClass(generateAdapter = true)
data class ManifestItemDto(
    @Json(name = "media_id") val mediaId: String,
    @Json(name = "checksum") val checksum: String,
    @Json(name = "type") val type: String, // "IMAGE" | "VIDEO"
    @Json(name = "order") val order: Int,
    @Json(name = "seconds") val seconds: Int,
    @Json(name = "download_url") val downloadUrl: String,
)

@JsonClass(generateAdapter = true)
data class ManifestResponseDto(
    @Json(name = "publication_id") val publicationId: String?,
    @Json(name = "version") val version: Int,
    @Json(name = "generated_at") val generatedAt: String?,
    @Json(name = "items") val items: List<ManifestItemDto>,
    @Json(name = "ttl_seconds") val ttlSeconds: Int,
)

// ---------------------------------------------------------------- 6) events

@JsonClass(generateAdapter = true)
data class PlaybackEventDto(
    @Json(name = "type") val type: String,
    @Json(name = "occurred_at") val occurredAt: String,
    @Json(name = "confirmed") val confirmed: Boolean,
    @Json(name = "details") val details: Map<String, Any?> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class EventsRequestDto(
    @Json(name = "events") val events: List<PlaybackEventDto>,
)

@JsonClass(generateAdapter = true)
data class EventsResponseDto(
    @Json(name = "accepted") val accepted: Int,
)

// ---------------------------------------------------------------- unified error envelope

@JsonClass(generateAdapter = true)
data class ApiErrorBodyDto(
    @Json(name = "code") val code: String,
    @Json(name = "message_ar") val messageAr: String? = null,
    @Json(name = "field_errors") val fieldErrors: Map<String, String> = emptyMap(),
    @Json(name = "correlation_id") val correlationId: String? = null,
)
