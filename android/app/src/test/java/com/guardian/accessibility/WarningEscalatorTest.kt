package com.guardian.accessibility

import com.guardian.accessibility.WarningEscalator.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Escalation policy: warn on strikes 1-2, mask from strike 3.
 * Pure JVM test — run with:  ./gradlew :app:testDebugUnitTest
 */
class WarningEscalatorTest {

    private val pkg = "com.twitter.android"

    @Test
    fun `first two strikes warn, third masks`() {
        val e = WarningEscalator()
        assertEquals(Action.WARN, e.onToxic(1_000L, pkg))
        assertEquals(Action.WARN, e.onToxic(2_000L, pkg))
        assertEquals(Action.MASK, e.onToxic(3_000L, pkg))
    }

    @Test
    fun `strikes keep masking after the threshold`() {
        val e = WarningEscalator()
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        assertEquals(Action.MASK, e.onToxic(3_000L, pkg))
        assertEquals(Action.MASK, e.onToxic(4_000L, pkg))
        assertEquals(4, e.strikeCount)
    }

    @Test
    fun `clean batches below reset threshold do not clear`() {
        val e = WarningEscalator(cleanChecksToReset = 2)
        e.onToxic(1_000L, pkg)
        assertEquals(Action.NONE, e.onClean(2_000L, pkg))
        assertEquals(1, e.strikeCount)
    }

    @Test
    fun `two consecutive clean batches clear and reset strikes`() {
        val e = WarningEscalator(cleanChecksToReset = 2)
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        e.onClean(3_000L, pkg)
        assertEquals(Action.CLEAR, e.onClean(4_000L, pkg))
        assertEquals(0, e.strikeCount)
        // After a reset the next toxic hit starts the ladder again.
        assertEquals(Action.WARN, e.onToxic(5_000L, pkg))
    }

    @Test
    fun `clean when there were never any strikes is a no-op`() {
        val e = WarningEscalator()
        assertEquals(Action.NONE, e.onClean(1_000L, pkg))
        assertEquals(Action.NONE, e.onClean(2_000L, pkg))
    }

    @Test
    fun `stale strikes expire after the window and restart at warn`() {
        val e = WarningEscalator(strikeWindowMs = 60_000L)
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        assertEquals(2, e.strikeCount)
        // Long gap — the old run is stale, so this must not jump straight to MASK.
        assertEquals(Action.WARN, e.onToxic(200_000L, pkg))
        assertEquals(1, e.strikeCount)
    }

    @Test
    fun `strikes within the window still escalate`() {
        val e = WarningEscalator(strikeWindowMs = 60_000L)
        e.onToxic(1_000L, pkg)
        e.onToxic(30_000L, pkg)
        assertEquals(Action.MASK, e.onToxic(59_000L, pkg))
    }

    @Test
    fun `switching apps resets strikes and clears the overlay`() {
        val e = WarningEscalator()
        e.observePackage(pkg)
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        assertEquals(2, e.strikeCount)

        // Foreground app changed — previous app's strikes must not carry over.
        assertEquals(Action.WARN, e.onToxic(3_000L, "com.instagram.android"))
        assertEquals(1, e.strikeCount)
    }

    @Test
    fun `app switch is detected even without an explicit observePackage call`() {
        // Regression: currentPackage was only latched in observePackage/hardReset,
        // so it stayed null through normal operation and app switches never fired.
        val e = WarningEscalator()
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        assertEquals(Action.WARN, e.onToxic(3_000L, "com.instagram.android"))
        assertEquals(1, e.strikeCount)
    }

    @Test
    fun `clean verdict in a new app clears any active overlay`() {
        val e = WarningEscalator()
        e.observePackage(pkg)
        e.onToxic(1_000L, pkg)
        assertEquals(Action.CLEAR, e.onClean(2_000L, "com.android.chrome"))
        assertEquals(0, e.strikeCount)
    }

    @Test
    fun `reset clears all state`() {
        val e = WarningEscalator()
        e.onToxic(1_000L, pkg)
        e.onToxic(2_000L, pkg)
        e.reset()
        assertEquals(0, e.strikeCount)
        assertEquals(Action.WARN, e.onToxic(3_000L, pkg))
    }

    @Test
    fun `threshold is configurable`() {
        val e = WarningEscalator(strikesBeforeMask = 2)
        assertEquals(Action.WARN, e.onToxic(1_000L, pkg))
        assertEquals(Action.MASK, e.onToxic(2_000L, pkg))
    }

    @Test
    fun `null package does not trigger a spurious reset`() {
        val e = WarningEscalator()
        e.onToxic(1_000L, null)
        e.onToxic(2_000L, null)
        assertEquals(Action.MASK, e.onToxic(3_000L, null))
    }
}
