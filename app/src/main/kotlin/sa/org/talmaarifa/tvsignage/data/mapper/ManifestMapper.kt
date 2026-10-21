package sa.org.talmaarifa.tvsignage.data.mapper

import sa.org.talmaarifa.tvsignage.core.Manifest
import sa.org.talmaarifa.tvsignage.core.ManifestItem
import sa.org.talmaarifa.tvsignage.core.MediaType
import sa.org.talmaarifa.tvsignage.network.dto.ManifestResponseDto

fun ManifestResponseDto.toDomain(): Manifest = Manifest(
    publicationId = publicationId,
    version = version,
    generatedAt = generatedAt,
    items = items.map {
        ManifestItem(
            mediaId = it.mediaId,
            checksum = it.checksum,
            type = when (it.type) {
                "IMAGE" -> MediaType.IMAGE
                "VIDEO" -> MediaType.VIDEO
                else -> MediaType.UNKNOWN
            },
            order = it.order,
            seconds = it.seconds,
            downloadPath = it.downloadUrl,
        )
    },
    ttlSeconds = ttlSeconds,
)
