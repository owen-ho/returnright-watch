package sg.returnright.watch.data

/** One shared cooldown for startup, foreground resumes, polling, and failed requests. */
class RefreshPolicy(private var lastAttemptAt: Long = 0) {
    fun nextCheckDelay(now: Long): Long =
        (INTERVAL_MS - (now - lastAttemptAt).coerceAtLeast(0)).coerceIn(1_000L, INTERVAL_MS)

    fun tryAcquire(now: Long, manual: Boolean = false): Boolean {
        if (!manual && lastAttemptAt != 0L && now - lastAttemptAt < INTERVAL_MS) return false
        lastAttemptAt = now
        return true
    }

    companion object { const val INTERVAL_MS = 10 * 60 * 1000L }
}
