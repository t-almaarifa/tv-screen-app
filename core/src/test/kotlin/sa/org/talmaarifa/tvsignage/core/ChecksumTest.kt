package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ChecksumTest {

    @Test
    fun `sha256Hex of bytes matches known digest`() {
        // printf 'hello' | sha256sum
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Checksum.sha256Hex("hello".toByteArray()),
        )
    }

    @Test
    fun `sha256Hex of stream matches sha256Hex of bytes`() {
        val payload = "the quick brown fox jumps over the lazy dog".toByteArray()
        assertEquals(Checksum.sha256Hex(payload), Checksum.sha256Hex(ByteArrayInputStream(payload)))
    }

    @Test
    fun `matches is case-insensitive`() {
        assertTrue(Checksum.matches("ABCDEF", "abcdef"))
        assertFalse(Checksum.matches("abcdef", "abcdee"))
    }
}
