package sa.org.talmaarifa.tvsignage.sync

import com.squareup.moshi.Moshi
import sa.org.talmaarifa.tvsignage.core.Checksum
import sa.org.talmaarifa.tvsignage.core.ManifestItem
import sa.org.talmaarifa.tvsignage.network.DeviceApi
import sa.org.talmaarifa.tvsignage.network.dto.ApiErrorBodyDto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest

sealed interface DownloadOutcome {
    data class Success(val file: File, val bytesWritten: Long) : DownloadOutcome
    data class ChecksumMismatch(val expected: String, val actual: String) : DownloadOutcome
    /** docs/tv-device-api.md §5: 401, 404, 403 ATTACHMENT_INFECTED, 423 ATTACHMENT_NOT_SCANNED, 410 ATTACHMENT_FILE_MISSING. */
    data class HttpError(val httpStatus: Int, val code: String?) : DownloadOutcome
    data class NetworkError(val cause: IOException) : DownloadOutcome
}

/** [HttpError]s that will never succeed by themselves retrying the same item right away. */
fun DownloadOutcome.HttpError.isPermanentForThisItem(): Boolean = when (code) {
    "ATTACHMENT_INFECTED" -> true // quarantined; will never be served
    "NOT_FOUND" -> true // no longer in this device's current publication (likely superseded already)
    else -> httpStatus == 404
}

/**
 * Downloads one manifest item to a temp file, verifies its checksum, and only then moves it into its
 * final place (docs/tv-device-api.md §5 + §3.2.4's "no item plays until fully downloaded and
 * verified"). Today's backend never advertises `Accept-Ranges` (see
 * [sa.org.talmaarifa.tvsignage.core.DownloadResumption] in :core), so an interrupted transfer simply
 * discards its partial temp file; the next sync cycle re-requests the whole item from byte zero.
 */
class MediaDownloader(
    private val deviceApi: DeviceApi,
    private val storage: StorageLayout,
    moshi: Moshi,
) {
    private val errorAdapter = moshi.adapter(ApiErrorBodyDto::class.java)

    suspend fun download(item: ManifestItem): DownloadOutcome {
        val response = try {
            deviceApi.downloadMedia(item.downloadPath)
        } catch (e: IOException) {
            return DownloadOutcome.NetworkError(e)
        }

        if (!response.isSuccessful) {
            val code = response.errorBody()?.string()
                ?.let { runCatching { errorAdapter.fromJson(it) }.getOrNull()?.code }
            return DownloadOutcome.HttpError(response.code(), code)
        }

        val body = response.body() ?: return DownloadOutcome.HttpError(response.code(), null)
        val tempFile = storage.tempFile(item.mediaId)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            body.byteStream().use { input ->
                DigestOutputStream(FileOutputStream(tempFile), digest).use { out -> input.copyTo(out) }
            }
        } catch (e: IOException) {
            tempFile.delete() // interrupted mid-transfer: never leave a partial file under a trusted name
            return DownloadOutcome.NetworkError(e)
        }

        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
        if (!Checksum.matches(item.checksum, actualChecksum)) {
            tempFile.delete()
            return DownloadOutcome.ChecksumMismatch(expected = item.checksum, actual = actualChecksum)
        }

        val finalFile = storage.finalFile(item.mediaId)
        finalFile.delete()
        if (!tempFile.renameTo(finalFile)) {
            return DownloadOutcome.NetworkError(IOException("could not move verified download into place: ${item.mediaId}"))
        }
        return DownloadOutcome.Success(finalFile, finalFile.length())
    }
}
