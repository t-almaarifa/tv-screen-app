package sa.org.talmaarifa.tvsignage.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import sa.org.talmaarifa.tvsignage.R
import sa.org.talmaarifa.tvsignage.player.PlaybackUiState
import sa.org.talmaarifa.tvsignage.player.PlaybackViewModel
import sa.org.talmaarifa.tvsignage.player.PlaylistController
import sa.org.talmaarifa.tvsignage.player.buildExoPlayer

/**
 * Fullscreen, infinite loop of the last atomically-activated publication (docs/tv-device-api.md §4;
 * FR-25's "no partial content"). Deliberately has no network calls of its own — it only reacts to
 * [PlaybackViewModel.state], which mirrors Room, so it keeps playing untouched while offline and only
 * ever changes when the sync engine commits a fully-verified new publication.
 */
@Composable
fun PlaybackScreen(viewModel: PlaybackViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val player = remember { buildExoPlayer(context) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { viewModel.onItemStarted(it) }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(player.currentMediaItem?.mediaId, error.message ?: "exoplayer_error")
            }
        })
    }

    LaunchedEffect(uiState.items) {
        applyPlaylist(player, uiState)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (uiState.items.isEmpty()) {
            Text(text = stringResource(R.string.playback_no_content), color = Color.Gray)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        this.player = player
                    }
                },
            )
        }
    }
}

private fun applyPlaylist(player: ExoPlayer, uiState: PlaybackUiState) {
    if (uiState.items.isEmpty()) {
        player.stop()
        player.clearMediaItems()
        return
    }
    val mediaItems = PlaylistController.toMediaItems(uiState.items)
    val currentId = player.currentMediaItem?.mediaId
    val resumeIndex = uiState.items.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
    player.setMediaItems(mediaItems, resumeIndex, 0L)
    player.prepare()
    player.playWhenReady = true
}
