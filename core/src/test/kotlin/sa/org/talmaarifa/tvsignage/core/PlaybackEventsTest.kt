package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackEventsTest {

    @Test
    fun `directly observed events are confirmed`() {
        assertTrue(PlaybackEventFactory.downloadStarted("m1", 0).confirmed)
        assertTrue(PlaybackEventFactory.downloadVerified("m1", "cs", 0).confirmed)
        assertTrue(PlaybackEventFactory.playbackStarted("p1", "m1", 0).confirmed)
        assertTrue(PlaybackEventFactory.playbackError("boom", "m1", 0).confirmed)
    }

    @Test
    fun `offline continuation is only ever an estimate`() {
        val event = PlaybackEventFactory.contentContinuedOffline("p1", 0)
        assertFalse(event.confirmed)
        assertEquals(PlaybackEventType.HEARTBEAT, event.type)
    }

    @Test
    fun `batches never exceed the server's 200-event cap`() {
        val records = (1..450).map { PlaybackEventFactory.playbackStarted("p1", "m$it", it.toLong()) }
        val batches = chunkForUpload(records)

        assertEquals(3, batches.size)
        assertEquals(200, batches[0].size)
        assertEquals(200, batches[1].size)
        assertEquals(50, batches[2].size)
        assertEquals(records, batches.flatten())
    }

    @Test
    fun `empty backlog produces no batches`() {
        assertTrue(chunkForUpload(emptyList()).isEmpty())
    }
}
