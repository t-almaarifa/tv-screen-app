package sa.org.talmaarifa.tvsignage.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/** One ExoPlayer instance, configured to render both images and video in the same playlist. */
fun buildExoPlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context)
        .experimentalSetImageDecoderEnabled(true)
    return ExoPlayer.Builder(context, renderersFactory)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ALL }
}
