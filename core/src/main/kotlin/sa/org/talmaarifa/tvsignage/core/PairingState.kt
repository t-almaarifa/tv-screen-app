package sa.org.talmaarifa.tvsignage.core

/**
 * Client-side mirror of the pairing lifecycle (docs/signage-module.md §3.2.1, src/lib/tv/pairing.ts).
 * The server is the sole source of truth for status; this state machine only decides what the *app*
 * should show/do next for a given server response, and never invents a transition the server didn't
 * report (e.g. the app never locally marks a code "approved").
 */
sealed interface PairingState {
    data object Idle : PairingState
    data class AwaitingApproval(val code: String, val pollToken: String, val expiresAtEpochMillis: Long) : PairingState
    data class Approved(val deviceId: String, val credential: String) : PairingState
    data object Rejected : PairingState
    data object Expired : PairingState
    data class Failed(val message: String) : PairingState
}

/** Mirrors the `status` field of `POST /api/v1/tv/device/pair/status`. */
enum class PairingServerStatus { PENDING, APPROVED, REJECTED, EXPIRED }

data class PairingPollResult(
    val status: PairingServerStatus,
    val deviceId: String? = null,
    /** Only present on the *first* successful poll after approval — the server clears it after that. */
    val credential: String? = null,
)

object PairingStateMachine {

    fun requested(code: String, pollToken: String, expiresAtEpochMillis: Long): PairingState =
        PairingState.AwaitingApproval(code, pollToken, expiresAtEpochMillis)

    /**
     * Applies a poll result on top of an [AwaitingApproval] state. If the credential wasn't delivered
     * on this particular poll (it is one-time-only server-side, §3.2.2) the app must keep whatever
     * credential it already captured rather than overwrite it with null — callers pass [previouslyCapturedCredential]
     * for that.
     */
    fun afterPoll(
        current: PairingState.AwaitingApproval,
        result: PairingPollResult,
        previouslyCapturedCredential: String? = null,
    ): PairingState = when (result.status) {
        PairingServerStatus.PENDING -> current
        PairingServerStatus.REJECTED -> PairingState.Rejected
        PairingServerStatus.EXPIRED -> PairingState.Expired
        PairingServerStatus.APPROVED -> {
            val credential = result.credential ?: previouslyCapturedCredential
            val deviceId = result.deviceId
            if (credential != null && deviceId != null) {
                PairingState.Approved(deviceId, credential)
            } else {
                // Approved, but this device never captured the one-time credential (e.g. app was
                // killed between approval and the next poll). Nothing to authenticate with — the
                // operator must revoke and re-pair from the admin panel (§3.2.2 acceptance criterion).
                PairingState.Failed("تم اعتماد الجهاز لكن تعذّر استلام بيانات الاعتماد؛ ألغِ الاقتران وأعد المحاولة من لوحة الإدارة.")
            }
        }
    }

    /** Local, defensive expiry check between polls — the server is still authoritative on the next poll. */
    fun isLocallyExpired(state: PairingState.AwaitingApproval, nowEpochMillis: Long): Boolean =
        nowEpochMillis >= state.expiresAtEpochMillis
}
