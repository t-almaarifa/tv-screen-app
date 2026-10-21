package sa.org.talmaarifa.tvsignage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import sa.org.talmaarifa.tvsignage.pairing.PairingViewModel
import sa.org.talmaarifa.tvsignage.player.PlaybackViewModel

/** Hand-rolled factory to match the hand-rolled DI container in [TvSignageApp] (no Hilt/Dagger). */
class ViewModelFactory(private val app: TvSignageApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        PairingViewModel::class.java -> PairingViewModel(app.pairingRepository) as T
        PlaybackViewModel::class.java -> PlaybackViewModel(app.database.activePublicationDao(), app.eventLogRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
