package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairingStateMachineTest {

    private val awaiting = PairingStateMachine.requested("123456", "poll-token", expiresAtEpochMillis = 1_000L) as PairingState.AwaitingApproval

    @Test
    fun `pending keeps waiting`() {
        val next = PairingStateMachine.afterPoll(awaiting, PairingPollResult(PairingServerStatus.PENDING))
        assertEquals(awaiting, next)
    }

    @Test
    fun `rejected transitions to Rejected`() {
        val next = PairingStateMachine.afterPoll(awaiting, PairingPollResult(PairingServerStatus.REJECTED))
        assertEquals(PairingState.Rejected, next)
    }

    @Test
    fun `expired transitions to Expired`() {
        val next = PairingStateMachine.afterPoll(awaiting, PairingPollResult(PairingServerStatus.EXPIRED))
        assertEquals(PairingState.Expired, next)
    }

    @Test
    fun `approved with a freshly delivered one-time credential captures it`() {
        val next = PairingStateMachine.afterPoll(
            awaiting,
            PairingPollResult(PairingServerStatus.APPROVED, deviceId = "dev-1", credential = "tv_dev-1.secret"),
        )
        assertEquals(PairingState.Approved("dev-1", "tv_dev-1.secret"), next)
    }

    @Test
    fun `approved on a later poll reuses the previously captured credential, server sends it only once`() {
        val next = PairingStateMachine.afterPoll(
            awaiting,
            PairingPollResult(PairingServerStatus.APPROVED, deviceId = "dev-1", credential = null),
            previouslyCapturedCredential = "tv_dev-1.secret",
        )
        assertEquals(PairingState.Approved("dev-1", "tv_dev-1.secret"), next)
    }

    @Test
    fun `approved with no credential ever captured fails instead of authenticating with nothing`() {
        val next = PairingStateMachine.afterPoll(
            awaiting,
            PairingPollResult(PairingServerStatus.APPROVED, deviceId = "dev-1", credential = null),
            previouslyCapturedCredential = null,
        )
        assertTrue(next is PairingState.Failed)
    }

    @Test
    fun `local expiry check`() {
        assertTrue(PairingStateMachine.isLocallyExpired(awaiting, nowEpochMillis = 1_000L))
        assertTrue(PairingStateMachine.isLocallyExpired(awaiting, nowEpochMillis = 1_001L))
        assertEquals(false, PairingStateMachine.isLocallyExpired(awaiting, nowEpochMillis = 999L))
    }
}
