package sa.org.talmaarifa.tvsignage.data.repo

import sa.org.talmaarifa.tvsignage.core.PairingPollResult
import sa.org.talmaarifa.tvsignage.core.PairingServerStatus
import sa.org.talmaarifa.tvsignage.core.PairingState
import sa.org.talmaarifa.tvsignage.core.PairingStateMachine
import sa.org.talmaarifa.tvsignage.data.credentials.CredentialStore
import sa.org.talmaarifa.tvsignage.network.ApiCaller
import sa.org.talmaarifa.tvsignage.network.ApiResult
import sa.org.talmaarifa.tvsignage.network.PairingApi
import sa.org.talmaarifa.tvsignage.network.dto.PairStatusRequestDto
import java.time.Instant

/**
 * Thin wrapper over [PairingApi] that hands results to [PairingStateMachine] (the pure decision
 * logic lives in :core; this only does I/O and persists the outcome). Mirrors docs/tv-device-api.md
 * §1-2 exactly.
 */
class PairingRepository(
    private val pairingApi: PairingApi,
    private val apiCaller: ApiCaller,
    private val credentialStore: CredentialStore,
) {
    suspend fun requestPairing(): ApiResult<PairingState.AwaitingApproval> =
        when (val r = apiCaller.call { pairingApi.requestPairing() }) {
            is ApiResult.Success -> {
                val expiresAtMillis = runCatching { Instant.parse(r.value.expiresAt).toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis() + 5 * 60_000L)
                ApiResult.Success(PairingStateMachine.requested(r.value.code, r.value.pollToken, expiresAtMillis) as PairingState.AwaitingApproval)
            }
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    /**
     * [previouslyCapturedCredential] must be whatever this device already captured on an earlier poll
     * of the *same* pairing attempt (the server sends the one-time credential exactly once).
     */
    suspend fun poll(
        current: PairingState.AwaitingApproval,
        previouslyCapturedCredential: String?,
    ): ApiResult<PairingState> = when (val r = apiCaller.call { pairingApi.pairingStatus(PairStatusRequestDto(current.pollToken)) }) {
        is ApiResult.Success -> {
            val status = runCatching { PairingServerStatus.valueOf(r.value.status.uppercase()) }.getOrNull()
            if (status == null) {
                ApiResult.ApiError(200, "UNKNOWN_STATUS", "استجابة غير متوقعة من الخادم: ${r.value.status}")
            } else {
                val next = PairingStateMachine.afterPoll(
                    current,
                    PairingPollResult(status, r.value.deviceId, r.value.credential),
                    previouslyCapturedCredential,
                )
                if (next is PairingState.Approved) {
                    credentialStore.save(next.deviceId, next.credential)
                }
                ApiResult.Success(next)
            }
        }
        is ApiResult.ApiError -> r
        is ApiResult.NetworkError -> r
    }
}
