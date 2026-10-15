package sg.returnright.watch.data

import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun `first check happens immediately then waits ten minutes`() {
        val policy = RefreshPolicy()
        assertTrue(policy.tryAcquire(now))
        assertFalse(policy.tryAcquire(now + 60_000))
        assertFalse(policy.tryAcquire(now + RefreshPolicy.INTERVAL_MS - 1))
        assertTrue(policy.tryAcquire(now + RefreshPolicy.INTERVAL_MS))
    }

    @Test fun `restart or foreground resume does not bypass cooldown`() {
        val policy = RefreshPolicy(now)
        assertFalse(policy.tryAcquire(now + 5 * 60_000))
        assertEquals(5 * 60_000L, policy.nextCheckDelay(now + 5 * 60_000))
        assertTrue(policy.tryAcquire(now + RefreshPolicy.INTERVAL_MS))
    }

    @Test fun `failed attempts have the same cooldown`() {
        val policy = RefreshPolicy()
        assertTrue(policy.tryAcquire(now))
        // No success callback is needed to retain the cooldown.
        assertFalse(policy.tryAcquire(now + 60_000))
    }

    @Test fun `manual refresh immediately bypasses cooldown including after failure`() {
        val policy = RefreshPolicy(now)
        assertTrue(policy.tryAcquire(now, manual = true))
        assertTrue(policy.tryAcquire(now + 1, manual = true))
        assertTrue(policy.tryAcquire(now + 10_000, manual = true))
        assertFalse(policy.tryAcquire(now + RefreshPolicy.INTERVAL_MS))
        assertTrue(policy.tryAcquire(now + 10_000 + RefreshPolicy.INTERVAL_MS))
    }

    @Test fun `backwards clock does not trigger repeated requests`() {
        assertFalse(RefreshPolicy(now).tryAcquire(now - 1_000))
    }
}
