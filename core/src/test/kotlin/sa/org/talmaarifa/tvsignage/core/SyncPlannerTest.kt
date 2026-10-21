package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncPlannerTest {

    private fun item(id: String, checksum: String = "cs-$id", order: Int = 0) =
        ManifestItem(mediaId = id, checksum = checksum, type = MediaType.IMAGE, order = order, seconds = 10, downloadPath = "/api/v1/tv/device/media/$id/download")

    @Test
    fun `fresh device with no local media must download every item`() {
        val remote = Manifest("pub-1", version = 1, generatedAt = null, items = listOf(item("a"), item("b")), ttlSeconds = 30)
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = null, currentLocalVersion = 0, localMedia = emptyMap())

        assertFalse(plan.alreadyActive)
        assertEquals(setOf("a", "b"), plan.itemsToDownload.map { it.mediaId }.toSet())
        assertFalse(SyncPlanner.shouldActivate(plan, verifiedMediaIds = emptySet()))
    }

    @Test
    fun `identical publication id and version needs no work`() {
        val remote = Manifest("pub-1", version = 3, generatedAt = null, items = listOf(item("a")), ttlSeconds = 30)
        val local = mapOf("a" to LocalMediaState("a", "cs-a", verified = true))
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-1", currentLocalVersion = 3, localMedia = local)

        assertTrue(plan.alreadyActive)
        assertTrue(plan.itemsToDownload.isEmpty())
        assertFalse(SyncPlanner.shouldActivate(plan, verifiedMediaIds = setOf("a")), "no point re-activating what is already active")
    }

    @Test
    fun `media no longer referenced is marked for eviction`() {
        val remote = Manifest("pub-2", version = 1, generatedAt = null, items = listOf(item("a")), ttlSeconds = 30)
        val local = mapOf(
            "a" to LocalMediaState("a", "cs-a", verified = true),
            "stale" to LocalMediaState("stale", "cs-stale", verified = true),
        )
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-1", currentLocalVersion = 1, localMedia = local)

        assertEquals(listOf("stale"), plan.mediaIdsToEvict)
    }

    @Test
    fun `connection drops mid-download - previous publication keeps playing, new one never activates`() {
        val remote = Manifest("pub-2", version = 2, generatedAt = null, items = listOf(item("a"), item("b")), ttlSeconds = 30)
        // "a" finished downloading and verified before the connection dropped; "b" never completed.
        val local = mapOf("a" to LocalMediaState("a", "cs-a", verified = true))
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-1", currentLocalVersion = 1, localMedia = local)

        assertEquals(listOf("b"), plan.itemsToDownload.map { it.mediaId })
        // Only "a" has been verified so far - the switch must not happen, and the device must keep
        // showing pub-1 (the caller simply doesn't touch the "current publication" pointer here).
        assertFalse(SyncPlanner.shouldActivate(plan, verifiedMediaIds = setOf("a")))
        assertFalse(plan.isCompleteFor(verifiedMediaIds = setOf("a")))
    }

    @Test
    fun `resuming after the outage and finishing the missing item allows the atomic switch`() {
        val remote = Manifest("pub-2", version = 2, generatedAt = null, items = listOf(item("a"), item("b")), ttlSeconds = 30)
        val local = mapOf("a" to LocalMediaState("a", "cs-a", verified = true))
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-1", currentLocalVersion = 1, localMedia = local)

        // Network comes back, "b" is re-downloaded from scratch (no Range support server-side,
        // see DownloadResumption) and its checksum now verifies too.
        val verifiedAfterResume = setOf("a", "b")
        assertTrue(plan.isCompleteFor(verifiedAfterResume))
        assertTrue(SyncPlanner.shouldActivate(plan, verifiedAfterResume))
    }

    @Test
    fun `a checksum mismatch on a locally cached file forces a re-download, not a silent accept`() {
        val remote = Manifest("pub-3", version = 1, generatedAt = null, items = listOf(item("a", checksum = "new-checksum")), ttlSeconds = 30)
        // Locally cached copy has a stale/corrupt checksum under the same media id.
        val local = mapOf("a" to LocalMediaState("a", "old-checksum", verified = true))
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-2", currentLocalVersion = 1, localMedia = local)

        assertEquals(listOf("a"), plan.itemsToDownload.map { it.mediaId })
    }

    @Test
    fun `an unresolved manifest (no active schedule, no default playlist) never activates`() {
        val remote = Manifest(publicationId = null, version = 0, generatedAt = null, items = emptyList(), ttlSeconds = 30)
        val plan = SyncPlanner.plan(remote, currentLocalPublicationId = "pub-1", currentLocalVersion = 1, localMedia = emptyMap())

        assertFalse(SyncPlanner.shouldActivate(plan, verifiedMediaIds = emptySet()))
    }
}
