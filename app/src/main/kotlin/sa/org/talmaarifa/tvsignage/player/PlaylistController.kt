package sa.org.talmaarifa.tvsignage.player

import android.net.Uri
import androidx.media3.common.MediaItem
import java.io.File

/**
 * Builds the ExoPlayer playlist for one [PlaybackUiState]. Images and videos share a single
 * ExoPlayer timeline (Media3 1.3+'s `MediaItem.Builder#setImageDurationMs`, paired with
 * `DefaultRenderersFactory#experimentalSetImageDecoderEnabled` — see
 * [sa.org.talmaarifa.tvsignage.player.buildExoPlayer]) so a signage loop can freely mix both without
 * two separate playback engines. Files are read straight from app-internal storage (`file://`), never
 * shared outside the app.
 */
object PlaylistController {
    fun toMediaItems(items: List<PlayableItem>): List<MediaItem> = items.map { item ->
        val uri = Uri.fromFile(File(item.localPath))
        val builder = MediaItem.Builder().setMediaId(item.mediaId).setUri(uri)
        if (item.type == "IMAGE") {
            builder.setImageDurationMs(item.seconds.coerceAtLeast(1) * 1_000L)
        }
        builder.build()
    }
}
