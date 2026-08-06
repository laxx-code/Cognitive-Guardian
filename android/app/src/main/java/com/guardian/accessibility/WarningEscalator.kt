package com.guardian.accessibility

/**
 * Escalation policy for interventions.
 *
 * Strikes 1..(strikesBeforeMask-1) produce a WARN (reframe sheet only).
 * Strike `strikesBeforeMask` and beyond produce a MASK (sheet + spatial overlay).
 *
 * Strikes decay so a user isn't punished for something they scrolled past ten
 * minutes ago:
 *  - they expire after [strikeWindowMs] with no toxic verdict,
 *  - they reset after [cleanChecksToReset] consecutive clean verdicts,
 *  - they reset immediately when the foreground app changes.
 *
 * Deliberately free of Android imports so it can be unit-tested on the JVM.
 * All time is passed in by the caller — no clock access here.
 */
class WarningEscalator(
    private val strikesBeforeMask: Int = 3,
    private val strikeWindowMs: Long = 120_000L,
    private val cleanChecksToReset: Int = 2
) {

    enum class Action {
        /** Do nothing. */
        NONE,

        /** Show the reframe sheet, no spatial mask. */
        WARN,

        /** Show the reframe sheet AND mask the flagged content. */
        MASK,

        /** Tear down any active sheet/overlay. */
        CLEAR
    }

    private var strikes: Int = 0
    private var lastStrikeAt: Long = 0L
    private var consecutiveClean: Int = 0
    private var currentPackage: String? = null

    val strikeCount: Int get() = strikes

    /** Records a toxic verdict and returns what the UI should do. */
    fun onToxic(now: Long, packageName: String?): Action {
        if (packageChanged(packageName)) {
            hardReset(packageName)
        } else if (strikes > 0 && now - lastStrikeAt > strikeWindowMs) {
            // Previous run of strikes went stale — start a fresh run.
            strikes = 0
        }

        // Must latch the package on every call, otherwise currentPackage stays null
        // forever and packageChanged() can never fire.
        if (packageName != null) currentPackage = packageName

        consecutiveClean = 0
        lastStrikeAt = now
        strikes++

        return if (strikes >= strikesBeforeMask) Action.MASK else Action.WARN
    }

    /**
     * Records a clean verdict. Returns [Action.CLEAR] once enough consecutive
     * clean batches have accumulated (or the app changed), otherwise [Action.NONE].
     */
    fun onClean(now: Long, packageName: String?): Action {
        if (packageChanged(packageName)) {
            hardReset(packageName)
            return Action.CLEAR
        }

        if (packageName != null) currentPackage = packageName

        if (strikes == 0) return Action.NONE

        consecutiveClean++
        if (consecutiveClean >= cleanChecksToReset) {
            strikes = 0
            consecutiveClean = 0
            return Action.CLEAR
        }
        return Action.NONE
    }

    /** Clears all state, e.g. on service disconnect. */
    fun reset() {
        strikes = 0
        consecutiveClean = 0
        lastStrikeAt = 0L
        currentPackage = null
    }

    private fun packageChanged(packageName: String?): Boolean =
        packageName != null && currentPackage != null && packageName != currentPackage

    private fun hardReset(packageName: String?) {
        strikes = 0
        consecutiveClean = 0
        lastStrikeAt = 0L
        currentPackage = packageName
    }

    /** Records the package without altering strike state (first observation). */
    fun observePackage(packageName: String?) {
        if (currentPackage == null) currentPackage = packageName
    }
}
