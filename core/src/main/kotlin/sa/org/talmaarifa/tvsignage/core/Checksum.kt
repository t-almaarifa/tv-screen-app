package sa.org.talmaarifa.tvsignage.core

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 hex digest helpers. The server stores `checksum` as a lowercase SHA-256 hex digest
 * (`checksum_sha256` in src/lib/tv/media.ts) — this must match exactly, byte for byte, before a
 * downloaded file is trusted.
 */
object Checksum {
    private const val ALGORITHM = "SHA-256"
    private const val BUFFER_SIZE = 64 * 1024

    fun sha256Hex(bytes: ByteArray): String = digestHex(MessageDigest.getInstance(ALGORITHM).digest(bytes))

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digestHex(digest.digest())
    }

    private fun digestHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** Case-insensitive compare: the server always lower-cases, but never trust that blindly. */
    fun matches(expected: String, actual: String): Boolean = expected.equals(actual, ignoreCase = true)
}
