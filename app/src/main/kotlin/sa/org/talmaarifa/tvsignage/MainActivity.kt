package sa.org.talmaarifa.tvsignage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import sa.org.talmaarifa.tvsignage.pairing.PairingViewModel
import sa.org.talmaarifa.tvsignage.player.PlaybackViewModel
import sa.org.talmaarifa.tvsignage.service.SignageForegroundService
import sa.org.talmaarifa.tvsignage.sync.ManifestSyncWorker
import sa.org.talmaarifa.tvsignage.ui.PairingScreen
import sa.org.talmaarifa.tvsignage.ui.PlaybackScreen
import sa.org.talmaarifa.tvsignage.ui.theme.TvSignageTheme

/**
 * Single-activity host: shows [PairingScreen] until a credential exists, then [PlaybackScreen].
 * Network/sync work keeps running in [SignageForegroundService] regardless of this activity's
 * lifecycle — a TV can blank/recreate its surface without interrupting the heartbeat loop.
 */
class MainActivity : ComponentActivity() {

    private val factory by lazy { ViewModelFactory(TvSignageApp.from(this)) }
    private val pairingViewModel: PairingViewModel by viewModels { factory }
    private val playbackViewModel: PlaybackViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SignageForegroundService.start(this)
        ManifestSyncWorker.schedule(this)

        setContent {
            TvSignageTheme {
                val isPaired by TvSignageApp.from(this).credentialStore.isPaired.collectAsState()
                if (isPaired) {
                    PlaybackScreen(playbackViewModel)
                } else {
                    val pairingState by pairingViewModel.state.collectAsState()
                    PairingScreen(pairingState)
                }
            }
        }
    }
}
