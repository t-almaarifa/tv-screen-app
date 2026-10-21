package sa.org.talmaarifa.tvsignage.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetryScheduleTest {

    @Test
    fun `grows exponentially from the base`() {
        assertEquals(5L, RetrySchedule.delaySeconds(attempt = 0, baseSeconds = 5, capSeconds = 300))
        assertEquals(10L, RetrySchedule.delaySeconds(attempt = 1, baseSeconds = 5, capSeconds = 300))
        assertEquals(20L, RetrySchedule.delaySeconds(attempt = 2, baseSeconds = 5, capSeconds = 300))
        assertEquals(40L, RetrySchedule.delaySeconds(attempt = 3, baseSeconds = 5, capSeconds = 300))
    }

    @Test
    fun `never exceeds the cap even after many attempts`() {
        val delay = RetrySchedule.delaySeconds(attempt = 20, baseSeconds = 5, capSeconds = 300)
        assertEquals(300L, delay)
    }

    @Test
    fun `jitter only ever adds on top of the capped value`() {
        val base = RetrySchedule.delaySeconds(attempt = 10, baseSeconds = 5, capSeconds = 300, jitterFraction = 0.0)
        val withJitter = RetrySchedule.delaySeconds(attempt = 10, baseSeconds = 5, capSeconds = 300, jitterFraction = 0.5)
        assertTrue(withJitter >= base)
    }
}
