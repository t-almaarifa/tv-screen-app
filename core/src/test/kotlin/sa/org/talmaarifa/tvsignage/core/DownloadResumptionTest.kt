package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadResumptionTest {

    @Test
    fun `no partial bytes always starts from zero`() {
        val decision = DownloadResumption.decide(bytesWrittenSoFar = 0, capability = DownloadServerCapability(acceptsRanges = true, contentLength = 1000))
        assertEquals(DownloadStrategy.START_FROM_ZERO, decision.strategy)
        assertEquals(0L, decision.resumeFromByte)
    }

    @Test
    fun `against today's backend, which never advertises Accept-Ranges, a partial file is discarded and restarted`() {
        // src/lib/services/files.ts openPrivateFile() sets no Accept-Ranges header and ignores Range
        // requests entirely, so acceptsRanges is always false against the real server today.
        val decision = DownloadResumption.decide(bytesWrittenSoFar = 4096, capability = DownloadServerCapability(acceptsRanges = false, contentLength = 9000))
        assertEquals(DownloadStrategy.START_FROM_ZERO, decision.strategy)
        assertEquals(0L, decision.resumeFromByte)
    }

    @Test
    fun `a hypothetical range-capable backend would allow a true byte-offset resume`() {
        val decision = DownloadResumption.decide(bytesWrittenSoFar = 4096, capability = DownloadServerCapability(acceptsRanges = true, contentLength = 9000))
        assertEquals(DownloadStrategy.RESUME_FROM_OFFSET, decision.strategy)
        assertEquals(4096L, decision.resumeFromByte)
    }
}
