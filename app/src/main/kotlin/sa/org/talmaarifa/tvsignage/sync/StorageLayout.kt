package sa.org.talmaarifa.tvsignage.sync

import android.content.Context
import java.io.File

/**
 * Where downloaded media lives on disk. `.tmp/<mediaId>.part` is written to while a download is in
 * flight; it is only ever renamed into `media/<mediaId>` after its checksum verifies (see
 * [sa.org.talmaarifa.tvsignage.sync.MediaDownloader]) — a half-downloaded file is never visible under
 * its final name, and the player never reads from `.tmp/`.
 */
class StorageLayout(context: Context) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val tmpDir = File(mediaDir, ".tmp").apply { mkdirs() }

    fun finalFile(mediaId: String): File = File(mediaDir, mediaId)
    fun tempFile(mediaId: String): File = File(tmpDir, "$mediaId.part")

    fun freeBytes(): Long = mediaDir.usableSpace
}
