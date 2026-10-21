package sa.org.talmaarifa.tvsignage.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sa.org.talmaarifa.tvsignage.core.PlaybackEventFactory
import sa.org.talmaarifa.tvsignage.data.db.ActivePublicationDao
import sa.org.talmaarifa.tvsignage.data.repo.EventLogRepository

data class PlayableItem(val mediaId: String, val localPath: String, val type: String, val seconds: Int, val order: Int)

data class PlaybackUiState(val publicationId: String?, val items: List<PlayableItem>)

/**
 * Exposes exactly what [ActivePublicationDao.observe] holds — the last atomically-activated
 * publication — as a playback playlist. Because the sync engine only ever writes to this table in
 * one transaction (see `ActivePublicationDao.replaceActive`), this reactively swaps the whole
 * playlist in one step whenever a new version goes live, and otherwise keeps looping the same list
 * with zero network dependency while offline.
 */
class PlaybackViewModel(
    activePublicationDao: ActivePublicationDao,
    private val eventLogRepository: EventLogRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = activePublicationDao.observe()
        .map { withItems ->
            PlaybackUiState(
                publicationId = withItems?.publication?.publicationId,
                items = withItems?.items.orEmpty()
                    .sortedBy { it.order }
                    .map { PlayableItem(it.mediaId, it.localPath, it.type, it.seconds, it.order) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackUiState(null, emptyList()))

    fun onItemStarted(mediaId: String) {
        val publicationId = state.value.publicationId
        viewModelScope.launch {
            eventLogRepository.record(PlaybackEventFactory.playbackStarted(publicationId, mediaId, System.currentTimeMillis()))
        }
    }

    fun onPlaybackError(mediaId: String?, message: String) {
        viewModelScope.launch {
            eventLogRepository.record(PlaybackEventFactory.playbackError(message, mediaId, System.currentTimeMillis()))
        }
    }
}
