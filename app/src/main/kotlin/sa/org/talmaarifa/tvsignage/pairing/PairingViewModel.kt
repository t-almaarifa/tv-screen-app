package sa.org.talmaarifa.tvsignage.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sa.org.talmaarifa.tvsignage.core.PairingState
import sa.org.talmaarifa.tvsignage.data.repo.PairingRepository
import sa.org.talmaarifa.tvsignage.network.ApiResult

/**
 * Drives the pairing screen: request a code, poll it on a fixed short interval until the server
 * reports approved/rejected/expired (docs/tv-device-api.md §1-2 set no server-driven poll interval
 * for pairing — unlike the heartbeat, the app picks its own cadence here), and automatically request
 * a fresh code once the current one expires.
 */
class PairingViewModel(private val repository: PairingRepository) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var capturedCredential: String? = null

    init {
        viewModelScope.launch { runPairingLoop() }
    }

    private suspend fun runPairingLoop() {
        while (true) {
            val requested = when (val r = repository.requestPairing()) {
                is ApiResult.Success -> r.value
                else -> {
                    delay(POLL_INTERVAL_MILLIS)
                    continue
                }
            }
            _state.value = requested
            capturedCredential = null
            pollUntilSettled(requested)

            // Settled as Rejected/Expired/Failed: wait briefly, then loop back and request a new code.
            if (_state.value !is PairingState.Approved) {
                delay(RESTART_DELAY_MILLIS)
            } else {
                return // Approved: MainActivity switches screens; nothing left for this loop to do.
            }
        }
    }

    private suspend fun pollUntilSettled(initial: PairingState.AwaitingApproval) {
        var current = initial
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            val result = repository.poll(current, capturedCredential)
            val next = when (result) {
                is ApiResult.Success -> result.value
                is ApiResult.ApiError, is ApiResult.NetworkError -> current // transient failure: keep showing the same code, retry
            }
            if (next is PairingState.Approved) capturedCredential = next.credential
            _state.value = next
            when (next) {
                is PairingState.AwaitingApproval -> current = next
                else -> return // Approved / Rejected / Expired / Failed — settled
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 3_000L
        private const val RESTART_DELAY_MILLIS = 2_000L
    }
}
