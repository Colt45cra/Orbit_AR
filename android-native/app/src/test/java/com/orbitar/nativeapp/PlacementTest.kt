package com.orbitar.nativeapp
import org.junit.Assert.*
import org.junit.Test
class PlacementTest {
    @Test fun bottomStaysOnTriggerAtEveryTilt() {
        for (tilt in listOf(0f, 30f, 45f, 90f)) {
            val p = popupCorners(0.08f, 0.12f, tilt, 0.03f, -0.02f)
            assertEquals(0.001f, p[2].y, 0.00001f)
            assertEquals(-0.02f, p[2].z, 0.00001f)
            assertEquals(0.03f, (p[0].x+p[1].x)/2f, 0.00001f)
        }
    }
    @Test fun flatImageLiesOnTriggerAndUprightRises() {
        val flat = popupCorners(0.08f,0.12f,0f,0f,0f)
        assertEquals(flat[0].y,flat[2].y,0.00001f)
        val upright = popupCorners(0.08f,0.12f,90f,0f,0f)
        assertEquals(0.12f,upright[0].y-upright[2].y,0.00001f)
        assertEquals(upright[0].z,upright[2].z,0.00001f)
    }
}
