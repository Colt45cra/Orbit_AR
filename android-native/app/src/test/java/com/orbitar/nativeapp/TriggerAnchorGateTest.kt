package com.orbitar.nativeapp

import org.junit.Assert.*
import org.junit.Test

class TriggerAnchorGateTest {
    private fun pose(x:Float=0f)=floatArrayOf(x,0f,0f,0f,0f,0f,1f)
    @Test fun locksOnceAndDoesNotFollowReappearingTrigger() {
        val g=TriggerAnchorGate()
        assertFalse(g.update(pose(),1_000_000_000L))
        assertTrue(g.update(pose(),1_600_000_000L));g.locked()
        assertFalse(g.update(null,2_000_000_000L))
        assertFalse(g.update(pose(2f),3_000_000_000L))
        assertFalse(g.update(pose(2f),4_000_000_000L))
        assertFalse(g.needsAnchor)
    }
    @Test fun realignmentRequiresFreshStableObservation() {
        val g=TriggerAnchorGate();g.locked();g.requestLock()
        assertFalse(g.update(pose(1f),1_000_000_000L))
        assertFalse(g.update(null,1_400_000_000L))
        assertFalse(g.update(pose(1f),1_600_000_000L))
        assertTrue(g.update(pose(1f),2_200_000_000L))
    }
    @Test fun movingAndRotatingTriggerCannotLockEarly() {
        val g=TriggerAnchorGate()
        g.update(pose(),1_000_000_000L)
        assertFalse(g.update(pose(.1f),1_600_000_000L))
        val rotated=pose(.1f).also {it[5]=.70710677f;it[6]=.70710677f}
        assertFalse(g.update(rotated,2_200_000_000L))
        assertTrue(g.update(rotated,2_800_000_000L))
    }
}
