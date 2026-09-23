package com.orbitar.nativeapp

import org.junit.Assert.*
import org.junit.Test

class TriggerRecognitionTest {
    @Test fun rememberedPoseNeverReportsVisualLock() {
        assertEquals(TriggerTracking.REMEMBERED, triggerTracking(true, false, true, true))
        assertEquals(TriggerTracking.LOST, triggerTracking(true, false, false, true))
        assertEquals(TriggerTracking.LOCKED, triggerTracking(true, true, false, true))
        assertEquals(TriggerTracking.CAMERA_LIMITED, triggerTracking(false, true, true, true))
        assertEquals(TriggerTracking.SEARCHING, triggerTracking(true, false, false, false))
    }

    @Test fun cropRetainsExactPhysicalWidthRatio() {
        val b = triggerCrop(1200, 1600, .1f, .25f, .15f, .1f)
        assertEquals(TriggerCrop(120, 400, 900, 1040), b)
        assertEquals(7.5f, 10f * b.width / 1200, .001f)
    }

    @Test fun cropBoundsRemainValidAtExtremesAndSmallSizes() {
        for (size in listOf(1, 2, 101, 2048)) {
            val b = triggerCrop(size, size, 1f, 1f, 1f, 1f)
            assertTrue(b.width > 0 && b.height > 0)
            assertTrue(b.left + b.width <= size && b.top + b.height <= size)
        }
        assertEquals(TriggerCrop(0, 0, 100, 200), triggerCrop(100, 200, 0f, 0f, 0f, 0f))
    }
}
