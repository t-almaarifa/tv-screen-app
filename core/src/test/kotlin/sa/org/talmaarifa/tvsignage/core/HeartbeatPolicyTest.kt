package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeartbeatPolicyTest {

    @Test
    fun `server value within range passes through unchanged`() {
        assertEquals(45, HeartbeatPolicy.clampIntervalSeconds(45))
    }

    @Test
    fun `null falls back to the documented server default`() {
        assertEquals(30, HeartbeatPolicy.clampIntervalSeconds(null))
    }

    @Test
    fun `a misbehaving server can't make the device poll too fast or go silent too long`() {
        assertEquals(HeartbeatPolicy.MIN_INTERVAL_SECONDS, HeartbeatPolicy.clampIntervalSeconds(0))
        assertEquals(HeartbeatPolicy.MIN_INTERVAL_SECONDS, HeartbeatPolicy.clampIntervalSeconds(-5))
        assertEquals(HeartbeatPolicy.MAX_INTERVAL_SECONDS, HeartbeatPolicy.clampIntervalSeconds(999_999))
    }

    @Test
    fun `refetch is driven by publication_changed, not guessed locally`() {
        assertTrue(HeartbeatPolicy.shouldRefetchManifest(publicationChanged = true, hasAnyLocalPublication = true))
        assertFalse(HeartbeatPolicy.shouldRefetchManifest(publicationChanged = false, hasAnyLocalPublication = true))
    }

    @Test
    fun `a brand-new device with nothing local fetches on the first heartbeat even if nothing changed`() {
        assertTrue(HeartbeatPolicy.shouldRefetchManifest(publicationChanged = false, hasAnyLocalPublication = false))
    }
}
